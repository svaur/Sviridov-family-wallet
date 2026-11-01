package ru.sviridov.wallet.parser;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a bank-feed screenshot (an image of the mobile-bank transaction list)
 * into a {@link ParsedStatement} using Tesseract OCR behind the scenes.
 *
 * <p>The screenshot has no "Назначение платежа" field, so {@link ParsedRow#purpose()}
 * is always empty and the merchant name + category are concatenated into
 * {@link ParsedRow#operation()}. Amounts are taken from the right-hand column
 * (a leading {@code -} marks an expense, {@code +} an income).
 *
 * <p>Amounts are anchored on the currency marker ({@code ₽}/{@code "руб"}): the
 * number right before the marker is the amount, everything left of it is text.
 * This is far more robust than a fixed column x, because device resolutions and
 * long merchant names shift the amount column around.
 */
@Component
public class ScreenshotParser implements StatementParser {

    private static final String TESSERACT = "tesseract";
    private static final Pattern SIGNED = Pattern.compile("^[−+\\-–—―].*\\d");
    private static final Pattern DAY_MONTH = Pattern.compile("^(\\d{1,2})\\s+(.+)$");
    private static final Pattern TIME = Pattern.compile("^\\d{1,2}[:.]\\d{2}$");
    /**
     * A signed number. Covers every dash the OCR may produce: U+2212 minus,
     * ASCII hyphen, U+2013 en dash, U+2014 em dash, U+2015 horizontal bar. The
     * bank feed uses U+2212 at full size, but at small glyph sizes Tesseract often
     * widens it to an em dash — hence the explicit list.
     */
    private static final Pattern SIGNED_TOKEN = Pattern.compile("^[−+\\-–—―]\\d+([.,]\\d+)?$");
    private static final Pattern NUMERIC_TOKEN = Pattern.compile("^\\d+([.,]\\d+)?$|^[.,]\\d+$");

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("января", 1), Map.entry("февраля", 2), Map.entry("марта", 3),
            Map.entry("апреля", 4), Map.entry("мая", 5), Map.entry("июня", 6),
            Map.entry("июля", 7), Map.entry("августа", 8), Map.entry("сентября", 9),
            Map.entry("октября", 10), Map.entry("ноября", 11), Map.entry("декабря", 12));

    private static final Set<String> NAV_WORDS = Set.of(
            "главный", "главная", "история", "платежи", "чаты", "оформить", "профиль", "копилка");

    private static final Set<String> MONTH_NAMES = Set.of(
            "январь", "февраль", "март", "апрель", "май", "июнь", "июль",
            "август", "сентябрь", "октябрь", "ноябрь", "декабрь",
            "января", "февраля", "марта", "апреля", "мая", "июня", "июля",
            "августа", "сентября", "октября", "ноября", "декабря");

    // Fallback vertical tolerance used only when the OCR gives too few lines to
    // measure a line pitch (e.g. a unit test with two lines).
    private static final float ROW_GAP = 0.01f;
    private static final float DETAILS_MAX_GAP = 0.03f;

    // Tolerances are expressed as multiples of the measured LINE PITCH (the median
    // distance between two consecutive lines), not as fractions of the image
    // height. A screenshot can be 12 000 px tall, where 1% of the height is 120 px —
    // several lines — and lines from different operations would be merged into one
    // "visual row". Pitch is density-independent, so the same constants work for
    // any device.
    private static final float PITCH_JITTER = 0.0008f;   // below this two tokens are on the same line
    private static final float ROW_TOL_FACTOR = 0.6f;    // two tokens within this share a visual row
    private static final float DETAILS_GAP_FACTOR = 1.5f; // a details line may sit this far below its title
    // When no currency marker survived the OCR, a number right of this fraction of
    // the width is still treated as the amount.
    private static final float RIGHT_AMOUNT_X = 0.55f;
    // The amount sits in the right part of the row. OCR'ing that strip on its own
    // with a digit-only whitelist gives a clean "5000 ₽" even when the full-row
    // reading merged the currency glyph into the number ("50002").
    // Everything in the top sliver of the image is app chrome (status bar, page
    // title, filter chips) — never an operation.
    private static final float CHROME_TOP = 0.03f;

    // --- geometric blank-space segmentation ---
    // The ink profile is computed over the FULL width. An icon may fuse a title
    // with its details into one block, but that is harmless: the block is OCR'd as
    // one crop and Tesseract still returns two lines, which the parser splits.
    // Rows with at most this fraction of ink pixels count as empty (relative to width).
    private static final float EMPTY_ROW_RATIO = 0.004f;
    // Gaps shorter than this are anti-aliasing, not separators.
    private static final int MIN_GAP_PX = 4;
    // Blocks closer than this fraction of the width are joined into one crop
    // (a title and its details line). Kept deliberately small: merging is only an
    // OCR-cost optimisation, never needed for correctness — the gap between two
    // real operations is always much larger than this.
    private static final float MERGE_GAP_RATIO = 0.03f;
    // Padding (px) added above/below each unit crop before OCR.
    private static final int CROP_PAD_PX = 3;
    // Brightness split: >INK is ink on a dark theme, <INK on a light theme.
    private static final int INK = 120;

    @Override
    public String version() {
        return "screenshot-1.0";
    }

    @Override
    public ParsedStatement parse(byte[] imageBytes) throws StatementParseException {
        return parseLines(ocr(imageBytes), LocalDate.now());
    }

    /**
     * Pure parsing of OCR lines (kept separate from the Tesseract call so the
     * line logic is unit-testable without an OCR binary).
     *
     * <p>The feed is a sequence of two-line blocks: a title line (merchant +
     * signed amount) followed by a details line (category, optionally carrying a
     * cashback amount that must be discarded). A small state machine walks the
     * lines top-to-bottom so a details line always belongs to the title line
     * directly above it — never to a "nearest" amount.
     *
     * @param lines         recognised lines with normalised coordinates (y: 0=top, 1=bottom)
     * @param referenceDate used to resolve "Сегодня"/"Вчера" and the year of dated entries
     */
    public ParsedStatement parseLines(List<OcrLine> lines, LocalDate referenceDate) {
        List<OcrLine> sorted = new ArrayList<>(lines);
        sorted.sort(Comparator.comparingDouble(OcrLine::y));

        // Vertical tolerances are multiples of the measured line pitch, so the same
        // constants behave the same on a 700-px crop and on a 12 000-px feed.
        float pitch = linePitch(sorted);
        float rowTol = pitch > 0f ? pitch * ROW_TOL_FACTOR : ROW_GAP;
        float detailsMax = pitch > 0f ? pitch * DETAILS_GAP_FACTOR : DETAILS_MAX_GAP;

        String cardNumber = null;
        LocalDate currentDate = null;
        State state = State.EXPECT_TITLE;
        Row current = null;
        List<Row> rows = new ArrayList<>();
        List<String> unrecognized = new ArrayList<>();
        StringBuilder orphan = new StringBuilder();

        for (VisualRow vr : groupRows(sorted, rowTol)) {
            String text = String.join(" ", vr.texts).trim();

            if (vr.y < CHROME_TOP) {
                continue; // status bar / page title / filter chips
            }
            if (cardNumber == null && isCardLine(text)) {
                cardNumber = maskedNumber(text);
                continue;
            }
            // Amount-only row (card balance "50 000 ₽", or a "Вы потратили … ₽"
            // summary whose text was dropped as noise) — discard it. It has no
            // merchant text, so it is never a real operation.
            if (vr.hasAmount() && vr.texts.isEmpty() && !vr.credit) {
                continue;
            }
            LocalDate date = parseDate(text, referenceDate);
            if (date != null) {
                flushOrphan(orphan, unrecognized);
                currentDate = date;
                state = State.EXPECT_TITLE;
                current = null;
                continue;
            }
            if (text.isEmpty() || isNoise(text)) {
                continue;
            }

            if (state == State.EXPECT_TITLE) {
                if (vr.hasAmount()) {
                    current = newRow(rowDate(currentDate, referenceDate), vr, rows.size() + 1);
                    rows.add(current);
                    state = State.EXPECT_DETAILS;
                } else if (currentDate != null) {
                    // Text without a recognised amount. Keep it as an operation with a
                    // zero amount instead of dropping it: removing a wrong row in the
                    // wizard is one click, re-typing a missing operation is much more
                    // work. Lines above the first date header are app chrome and stay
                    // out (currentDate is still null there).
                    current = newRow(rowDate(currentDate, referenceDate), vr, rows.size() + 1);
                    rows.add(current);
                    state = State.EXPECT_DETAILS;
                } else {
                    appendOrphan(orphan, text);
                }
            } else { // EXPECT_DETAILS
                boolean near = current != null && vr.y - current.y <= detailsMax;
                if (vr.hasAmount()) {
                    if (vr.credit && near) {
                        // Cashback on the details line: drop the amount, keep its text.
                        current.parts.addAll(vr.texts);
                    } else {
                        // A new operation (negative expense, or a positive income far below).
                        current = newRow(rowDate(currentDate, referenceDate), vr, rows.size() + 1);
                        rows.add(current);
                    }
                } else if (near) {
                    // Details line (text only) belongs to the open operation.
                    current.parts.addAll(vr.texts);
                } else if (currentDate != null) {
                    // Far text with no amount: treat it as another operation with an
                    // unknown amount rather than losing it.
                    current = newRow(rowDate(currentDate, referenceDate), vr, rows.size() + 1);
                    rows.add(current);
                } else {
                    appendOrphan(orphan, text);
                }
            }
        }
        flushOrphan(orphan, unrecognized);

        // Build the final rows (operation = merchant + details joined top-to-bottom).
        List<ParsedRow> parsedRows = new ArrayList<>();
        for (Row row : rows) {
            String operation = cleanOperation(String.join(" ", row.parts));
            if (operation.isEmpty()) {
                operation = row.row.operation() != null ? row.row.operation() : "Операция";
            }
            parsedRows.add(new ParsedRow(row.row.rowNumber(), row.row.date(), operation,
                    row.row.expense(), row.row.income(), ""));
        }

        LocalDate from = null;
        LocalDate to = null;
        for (ParsedRow r : parsedRows) {
            if (r.date() != null) {
                if (from == null || r.date().isBefore(from)) {
                    from = r.date();
                }
                if (to == null || r.date().isAfter(to)) {
                    to = r.date();
                }
            }
        }

        return new ParsedStatement(from, to, cardNumber, null, null,
                parsedRows.size(), parsedRows, unrecognized);
    }

    /**
     * Groups sorted OCR lines into visual rows: text tokens land in
     * {@link VisualRow#texts} and a recognised amount in {@link VisualRow#amount}.
     * A bare signed positive token that is not a real amount ("+5", "+180") is
     * cashback and is dropped entirely.
     */
    private static List<VisualRow> groupRows(List<OcrLine> sorted, float rowTol) {
        List<VisualRow> rows = new ArrayList<>();
        VisualRow current = null;
        for (OcrLine line : sorted) {
            if (isNoise(line.text())) {
                continue; // icons, clocks, "вы потратили", nav labels
            }
            if (current == null || Math.abs(line.y() - current.y) > rowTol) {
                current = new VisualRow(line.y());
                rows.add(current);
            }
            if (isAmount(line.text())) {
                if (current.amount == null) {
                    current.amount = line.text();
                    current.credit = isCredit(line.text());
                }
            } else if (SIGNED_TOKEN.matcher(line.text().trim()).matches()) {
                // A signed number that failed isAmount → cashback, drop it.
            } else {
                current.texts.add(line.text());
            }
        }
        return rows;
    }

    private static LocalDate rowDate(LocalDate currentDate, LocalDate referenceDate) {
        return currentDate != null ? currentDate : referenceDate;
    }

    /**
     * Median distance between two consecutive OCR lines — the line pitch of the
     * document. It is the natural unit for vertical tolerances: on a tall feed it
     * is a few dozen pixels, in a small crop it is a fraction of the height, and a
     * "1% of the image" constant would mean completely different things in the two
     * cases. Returns 0 when there are too few lines to measure.
     */
    private static float linePitch(List<OcrLine> sorted) {
        List<Float> deltas = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            float delta = sorted.get(i).y() - sorted.get(i - 1).y();
            if (delta > PITCH_JITTER) {
                deltas.add(delta);
            }
        }
        if (deltas.size() < 3) {
            return 0f;
        }
        deltas.sort(null);
        return deltas.get(deltas.size() / 2);
    }

    private static Row newRow(LocalDate date, VisualRow vr, int rowNumber) {
        BigDecimal amount = amountValue(vr.amount);
        ParsedRow row = new ParsedRow(rowNumber, date, null,
                vr.credit ? null : amount, vr.credit ? amount : null, "");
        Row r = new Row(row, vr.y);
        r.parts.addAll(vr.texts);
        return r;
    }

    /**
     * Final cleanup of an operation string: keep only Russian/English letters,
     * digits and the period; every other character becomes a single space, then
     * whitespace runs collapse and the result is trimmed.
     */
    private static String cleanOperation(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.replaceAll("[^A-Za-zА-Яа-яЁё0-9.]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static void appendOrphan(StringBuilder orphan, String text) {
        if (orphan.length() > 0) {
            orphan.append(' ');
        }
        orphan.append(text);
    }

    private static void flushOrphan(StringBuilder orphan, List<String> unrecognized) {
        if (orphan.length() > 0) {
            unrecognized.add(orphan.toString().trim());
            orphan.setLength(0);
        }
    }

    private List<OcrLine> ocr(byte[] imageBytes) throws StatementParseException {
        try {
            BufferedImage img;
            try (ByteArrayInputStream in = new ByteArrayInputStream(imageBytes)) {
                img = ImageIO.read(in);
            }
            if (img == null) {
                throw new StatementParseException("Не удалось прочитать изображение");
            }
            BufferedImage gray = sharpen(img);
            int fullH = gray.getHeight();

            // Cut the screenshot into units at its blank bands, then OCR each unit
            // separately. One operation (or date header) = one crop = one Tesseract
            // call, so two operations can never merge inside a single Tesseract line.
            List<OcrLine> all = new ArrayList<>();
            for (Unit unit : segmentUnits(gray)) {
                int top = Math.max(0, unit.y0 - CROP_PAD_PX);
                int bottom = Math.min(gray.getHeight() - 1, unit.y1 + CROP_PAD_PX);
                int cropH = bottom - top + 1;
                BufferedImage crop = gray.getSubimage(0, top, gray.getWidth(), cropH);
                for (OcrLine line : ocrCrop(crop)) {
                    // y is normalised to the crop height; shift it back to full-image space.
                    float fullY = (top + line.y() * cropH) / (float) fullH;
                    all.add(new OcrLine(line.text(), line.x(), fullY));
                }
            }
            all.sort(Comparator.comparingDouble(OcrLine::y));
            return all;
        } catch (IOException e) {
            throw new StatementParseException("Ошибка OCR: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StatementParseException("OCR прерван");
        }
    }

    /** Runs Tesseract on a single unit crop and returns its words as {@link OcrLine}s. */
    private static List<OcrLine> ocrCrop(BufferedImage crop)
            throws IOException, InterruptedException, StatementParseException {
        return runTesseract(crop, 2, "--psm", "6");
    }

    /** Writes the image to a temp file, runs Tesseract with TSV output and parses it. */
    private static List<OcrLine> runTesseract(BufferedImage image, int scale, String... extra)
            throws IOException, InterruptedException, StatementParseException {
        Path tmp = null;
        try {
            BufferedImage big = upscale(image, scale);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(big, "png", png);
            tmp = Files.createTempFile("screenshot", ".png");
            Files.write(tmp, png.toByteArray());

            List<String> cmd = new ArrayList<>(List.of(TESSERACT, tmp.toString(), "stdout", "-l", "rus+eng"));
            cmd.addAll(List.of(extra));
            cmd.add("tsv");
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String tsv = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = process.waitFor();
            if (code != 0) {
                throw new StatementParseException("OCR не выполнен (tesseract, код " + code + ")");
            }
            return parseTsv(tsv, big.getWidth(), big.getHeight());
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        }
    }

    /**
     * Splits the screenshot into vertical crops at its blank bands, so that one
     * crop contains one visual line (or a title plus its details).
     *
     * <p>The ink profile is computed over the full width — no column is excluded,
     * because the text does not start at a fixed fraction of the width (the fixture
     * has no icon gutter at all, some devices have a narrower one). Rows with
     * almost no ink are empty; consecutive empty rows form a gap.</p>
     *
     * <p>Blocks closer than {@link #MERGE_GAP_RATIO}·W are joined (title +
     * details). This is only a cost optimisation: two real operations are always
     * separated by a much larger gap, and every crop is OCR'd separately, so two
     * operations can never merge inside one Tesseract line.</p>
     */
    static List<Unit> segmentUnits(BufferedImage gray) {
        int W = gray.getWidth();
        int H = gray.getHeight();
        boolean dark = isDarkTheme(gray);
        int emptyThreshold = Math.max(1, (int) (EMPTY_ROW_RATIO * W));

        int[] ink = new int[H];
        for (int y = 0; y < H; y++) {
            int c = 0;
            for (int x = 0; x < W; x++) {
                int v = gray.getRGB(x, y) & 0xff;
                if (dark ? v > INK : v < INK) {
                    c++;
                }
            }
            ink[y] = c;
        }

        List<int[]> blocks = new ArrayList<>();
        int start = -1;
        for (int y = 0; y < H; y++) {
            boolean empty = ink[y] <= emptyThreshold;
            if (!empty && start < 0) {
                start = y;
            } else if (empty && start >= 0) {
                blocks.add(new int[]{start, y - 1});
                start = -1;
            }
        }
        if (start >= 0) {
            blocks.add(new int[]{start, H - 1});
        }
        if (blocks.isEmpty()) {
            return List.of();
        }

        int mergeGap = Math.max(MIN_GAP_PX, (int) (MERGE_GAP_RATIO * W));

        List<Unit> units = new ArrayList<>();
        Unit cur = null;
        for (int[] b : blocks) {
            if (cur == null) {
                cur = new Unit(b[0], b[1]);
            } else if (b[0] - cur.y1 - 1 < mergeGap) {
                cur = new Unit(cur.y0, b[1]);
            } else {
                units.add(cur);
                cur = new Unit(b[0], b[1]);
            }
        }
        if (cur != null) {
            units.add(cur);
        }
        return units;
    }

    /** Detects a dark theme by the median brightness of a middle stripe. */
    private static boolean isDarkTheme(BufferedImage gray) {
        int W = gray.getWidth();
        int H = gray.getHeight();
        long sum = 0;
        long n = 0;
        for (int y = H / 3; y < H * 2 / 3; y += 8) {
            for (int x = 0; x < W; x += 8) {
                sum += gray.getRGB(x, y) & 0xff;
                n++;
            }
        }
        return n == 0 || sum / n < 128;
    }

    /**
     * Prepares a screenshot for OCR: converts to grayscale (reduces colour
     * noise) and applies a 3×3 sharpen kernel to crisp up glyph edges before
     * handing the image to Tesseract.
     */
    static BufferedImage sharpen(BufferedImage image) {
        BufferedImage gray = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        try {
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        float[] data = {0f, -1f, 0f, -1f, 5f, -1f, 0f, -1f, 0f};
        Kernel kernel = new Kernel(3, 3, data);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        return op.filter(gray, null);
    }

    /**
     * Converts Tesseract's word-level TSV into {@link OcrLine}s. Tesseract's own
     * block/paragraph/line grouping is deliberately IGNORED — on this feed it
     * frequently merges two adjacent visual lines into one "line", which collapses
     * two operations into one (only the rightmost amount survives). Instead the
     * words are re-clustered into visual lines by their {@code y} coordinate, then
     * each line is anchored on its currency marker ({@code ₽} or {@code "руб"}):
     * the numeric tokens immediately left of the marker are the amount, everything
     * left of those is the merchant/detail text. A signed token terminates the
     * walk, so a merchant number ("Магазин № 24") is never mistaken for part of
     * the amount.
     */
    static List<OcrLine> parseTsv(String tsv, int width, int height) {
        List<Word> words = new ArrayList<>();
        for (String raw : tsv.split("\n")) {
            String[] c = raw.split("\t", -1);
            if (c.length < 12 || !"5".equals(c[0])) {
                continue; // word level only
            }
            float x = width > 0 ? parseFloat(c[6]) / width : parseFloat(c[6]);
            float y = height > 0 ? parseFloat(c[7]) / height : parseFloat(c[7]);
            float h = height > 0 ? parseFloat(c[9]) / height : 0f;
            words.add(new Word(c[11], x, y, h));
        }
        if (words.isEmpty()) {
            return List.of();
        }
        words.sort(Comparator.comparingDouble((Word w) -> w.y).thenComparingDouble(w -> w.x));

        List<OcrLine> lines = new ArrayList<>();
        for (List<Word> visualLine : clusterByY(words)) {
            visualLine.sort(Comparator.comparingDouble(w -> w.x));

            int anchor = findCurrencyAnchor(visualLine);
            if (anchor < 0) {
                // The currency glyph may have been misread as "PO"/"PR"/"2" or
                // dropped entirely — the number still sits in the amount column.
                anchor = findRightmostNumber(visualLine);
            }
            int amountStart = anchor < 0 ? visualLine.size() : anchor;
            if (anchor >= 0) {
                // Walk left collecting the amount; a signed token marks its start.
                amountStart = anchor;
                while (amountStart > 0) {
                    String w = visualLine.get(amountStart - 1).text;
                    if (SIGNED_TOKEN.matcher(w).matches()) {
                        amountStart--;
                        break;
                    }
                    if (NUMERIC_TOKEN.matcher(w).matches()) {
                        amountStart--;
                    } else {
                        break;
                    }
                }
            }

            if (amountStart > 0) {
                StringBuilder text = new StringBuilder();
                float minX = Float.MAX_VALUE;
                float minY = Float.MAX_VALUE;
                for (int i = 0; i < amountStart; i++) {
                    Word w = visualLine.get(i);
                    if (text.length() > 0) {
                        text.append(' ');
                    }
                    text.append(w.text);
                    minX = Math.min(minX, w.x);
                    minY = Math.min(minY, w.y);
                }
                lines.add(new OcrLine(text.toString().trim(),
                        minX == Float.MAX_VALUE ? 0f : minX,
                        minY == Float.MAX_VALUE ? 0f : minY));
            }

            if (anchor >= 0) {
                StringBuilder amount = new StringBuilder();
                float maxX = 0f;
                float minY = Float.MAX_VALUE;
                for (int i = amountStart; i <= anchor; i++) {
                    Word w = visualLine.get(i);
                    if (amount.length() > 0) {
                        amount.append(' ');
                    }
                    amount.append(w.text);
                    maxX = Math.max(maxX, w.x);
                    minY = Math.min(minY, w.y);
                }
                lines.add(new OcrLine(amount.toString().trim(), maxX,
                        minY == Float.MAX_VALUE ? 0f : minY));
            }
        }
        return lines;
    }

    /**
     * Clusters word tokens into visual lines by their {@code y} coordinate. A new
     * line starts when a word is more than ~0.8 × the median word height below the
     * line's first word. Tesseract's own line grouping is not trusted, because on
     * this feed it merges adjacent lines and collapses operations.
     */
    private static List<List<Word>> clusterByY(List<Word> sorted) {
        float lineTol = medianHeight(sorted) * 0.8f;
        if (lineTol <= 0f) {
            lineTol = 0.008f; // OCR reported no heights — fall back to a small gap
        }
        List<List<Word>> lines = new ArrayList<>();
        List<Word> current = null;
        float currentY = 0f;
        for (Word w : sorted) {
            if (current == null || w.y - currentY > lineTol) {
                current = new ArrayList<>();
                lines.add(current);
                currentY = w.y;
            }
            current.add(w);
        }
        return lines;
    }

    private static float medianHeight(List<Word> words) {
        float[] hs = new float[words.size()];
        int n = 0;
        for (Word w : words) {
            if (w.h > 0f) {
                hs[n++] = w.h;
            }
        }
        if (n == 0) {
            return 0f;
        }
        Arrays.sort(hs, 0, n);
        return hs[n / 2];
    }

    /** Returns the rightmost currency-marker word index, or -1 when there is none. */
    private static int findCurrencyAnchor(List<Word> words) {
        for (int i = words.size() - 1; i >= 0; i--) {
            if (hasCurrencyMarker(words.get(i).text)) {
                return i;
            }
        }
        // Fallback: a lone "p"/"р" that follows a number (OCR misread of ₽).
        for (int i = words.size() - 1; i >= 1; i--) {
            String t = words.get(i).text.trim().toLowerCase(Locale.ROOT);
            if ((t.equals("p") || t.equals("р")) && words.get(i - 1).text.matches(".*\\d.*")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Fallback anchor for lines whose currency glyph was mangled ("PO", "PR", "2")
     * or lost: the rightmost number-like token standing in the amount column.
     * Single-character tokens are skipped — they are almost always the misread
     * "₽" stuck to the amount, and including one would turn "1 200 2" into 12 002.
     * Header lines (card balance, account) are rejected so "Зарплатная карта 0884"
     * never becomes an operation.
     */
    private static int findRightmostNumber(List<Word> words) {
        for (int i = words.size() - 1; i >= 0; i--) {
            Word w = words.get(i);
            if (w.x < RIGHT_AMOUNT_X) {
                break; // left of the amount column: part of the text
            }
            String t = w.text.trim();
            if (t.length() == 1) {
                continue;
            }
            if (!NUMERIC_TOKEN.matcher(t).matches() && !SIGNED_TOKEN.matcher(t).matches()) {
                continue;
            }
            StringBuilder text = new StringBuilder();
            for (int j = 0; j < i; j++) {
                text.append(words.get(j).text).append(' ');
            }
            String lower = text.toString().toLowerCase(Locale.ROOT);
            if (lower.contains("карта") || lower.contains("баланс") || lower.contains("счет")) {
                return -1;
            }
            return i;
        }
        return -1;
    }

    private static String maskedNumber(String text) {
        String digits = text.replaceAll("\\D", "");
        return digits.length() >= 4 ? "••••" + digits.substring(digits.length() - 4) : text;
    }

    /**
     * A line holds the card number when it mentions "Карта" and carries at least
     * four digits (the masked tail). Tesseract does not reliably recognise the
     * {@code •} separator, so we deliberately do NOT require it here.
     */
    private static boolean isCardLine(String text) {
        return text.toLowerCase(Locale.ROOT).contains("карта") && text.replaceAll("\\D", "").length() >= 4;
    }

    private static boolean isAmount(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            return false;
        }
        boolean currency = hasCurrencyMarker(t);
        boolean signed = SIGNED.matcher(t).find();
        if (signed) {
            if (currency) {
                return true;
            }
            boolean decimal = t.contains(",") || t.contains(".");
            boolean thousands = t.matches(".*\\d[\\s\\u00A0]\\d.*");
            if (decimal || thousands) {
                return true;
            }
            // Bare signed number: a negative is an expense, a positive is cashback.
            return !isCredit(t);
        }
        // Unsigned: accept only a currency-marked number (OCR dropped the sign).
        // The balance line is unsigned + currency too, but parseLines skips it.
        return currency;
    }

    /** True when the text carries a ruble marker (₽ / "руб" / a lone or attached "p"/"р"). */
    private static boolean hasCurrencyMarker(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            return false;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        if (t.contains("₽") || lower.contains("руб")
                || t.equals("р") || t.equals("p")
                || lower.endsWith(" р") || lower.endsWith(" p")) {
            return true;
        }
        // Attached marker with no space, e.g. "980,74₽" / "511р" / "511p"
        // (Tesseract often glues the currency sign onto the number). Only accept
        // it when a digit immediately precedes the trailing "р"/"p", so a real
        // Russian word ending in "р" (e.g. "супер") is never treated as money.
        return lower.matches(".*\\d[рp]$");
    }

    private static boolean isCredit(String text) {
        String t = text == null ? "" : text.trim();
        return !t.isEmpty() && t.charAt(0) == '+';
    }

    /**
     * Turns an OCR amount token into a number. Never throws: a row with an
     * unreadable amount must survive with 0 so the user can correct it in the
     * wizard — dropping the operation would be much worse.
     */
    private static BigDecimal amountValue(String text) {
        if (text == null) {
            return BigDecimal.ZERO;
        }
        String digits = text.replaceAll("[^\\d.,]", "").replace(',', '.');
        if (digits.isEmpty() || digits.equals(".")) {
            return BigDecimal.ZERO;
        }
        int lastDot = digits.lastIndexOf('.');
        if (digits.indexOf('.') != lastDot) {
            // Several separators: all but the last are thousands separators
            // ("1.234.56" → "1234.56").
            digits = digits.substring(0, lastDot).replace(".", "") + digits.substring(lastDot);
            if (digits.startsWith(".")) {
                digits = "0" + digits;
            }
        }
        try {
            return new BigDecimal(digits);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static LocalDate parseDate(String text, LocalDate ref) {
        String t = text.trim();
        if ("сегодня".equalsIgnoreCase(t)) {
            return ref;
        }
        if ("вчера".equalsIgnoreCase(t)) {
            return ref.minusDays(1);
        }
        Matcher m = DAY_MONTH.matcher(t);
        if (!m.matches()) {
            return null;
        }
        int day;
        try {
            day = Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        String rest = m.group(2).toLowerCase(Locale.ROOT);
        Integer month = null;
        for (Map.Entry<String, Integer> e : MONTHS.entrySet()) {
            if (rest.startsWith(e.getKey()) || e.getKey().startsWith(rest)) {
                month = e.getValue();
                break;
            }
        }
        if (month == null) {
            return null;
        }
        LocalDate date = LocalDate.of(ref.getYear(), month, day);
        return date.isAfter(ref) ? date.minusYears(1) : date;
    }

    private static boolean isNoise(String text) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
        if (t.isEmpty()) {
            return true;
        }
        if (t.startsWith("вы потратили")) {
            return true;
        }
        // Nav / chrome labels. The bottom bar often arrives as one long line
        // ("Главный История Платежи Чаты Оформить"), so a line made up exclusively
        // of nav words counts as noise too.
        String[] words = t.split("\\s+");
        if (words.length == 1) {
            return NAV_WORDS.contains(t);
        }
        boolean allNav = true;
        for (String w : words) {
            if (!NAV_WORDS.contains(w)) {
                allNav = false;
                break;
            }
        }
        if (allNav) {
            return true;
        }
        // Month names (a month header between groups).
        if (MONTH_NAMES.contains(t)) {
            return true;
        }
        // Status-bar clock like "22:40" (or "22.40").
        if (TIME.matcher(t).matches()) {
            return true;
        }
        // Short, symbol-only tokens (icons, battery, signal dots). Two or more
        // consecutive letters are kept so real short merchants are not dropped.
        if (text.length() <= 3 && !text.matches(".*[а-яА-Яa-zA-Z]{2}.*") && !text.contains("₽")) {
            return true;
        }
        return false;
    }

    /** Bilinear upscale used before OCR to make small glyphs readable. */
    private static BufferedImage upscale(BufferedImage src, int factor) {
        BufferedImage out = new BufferedImage(src.getWidth() * factor, src.getHeight() * factor,
                BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        return out;
    }

    private static float parseFloat(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private enum State { EXPECT_TITLE, EXPECT_DETAILS }

    /** A vertical band of the screenshot holding one entity (operation, date header, summary). */
    static final class Unit {
        final int y0;
        final int y1;

        Unit(int y0, int y1) {
            this.y0 = y0;
            this.y1 = y1;
        }
    }

    /** A single Tesseract word with normalised coordinates. */
    private static final class Word {
        final String text;
        final float x;
        final float y;
        final float h;

        Word(String text, float x, float y, float h) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.h = h;
        }
    }

    /** A visual line: left-column text tokens plus an optional amount. */
    private static final class VisualRow {
        final float y;
        final List<String> texts = new ArrayList<>();
        String amount;
        boolean credit;

        VisualRow(float y) {
            this.y = y;
        }

        boolean hasAmount() {
            return amount != null;
        }
    }

    /** Mutable helper holding an amount row and the text lines that belong to it. */
    private static final class Row {
        final ParsedRow row;
        final float y;
        final List<String> parts = new ArrayList<>();

        Row(ParsedRow row, float y) {
            this.row = row;
            this.y = y;
        }
    }
}
