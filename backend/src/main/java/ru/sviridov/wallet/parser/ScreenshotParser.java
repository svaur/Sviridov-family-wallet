package ru.sviridov.wallet.parser;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    private static final Pattern SIGNED = Pattern.compile("^[−+\\-–].*\\d");
    private static final Pattern DAY_MONTH = Pattern.compile("^(\\d{1,2})\\s+(.+)$");
    private static final Pattern TIME = Pattern.compile("^\\d{1,2}[:.]\\d{2}$");
    private static final Pattern SIGNED_TOKEN = Pattern.compile("^[−+\\-–]\\d+([.,]\\d+)?$");
    private static final Pattern NUMERIC_TOKEN = Pattern.compile("^\\d+([.,]\\d+)?$|^[.,]\\d+$");

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("января", 1), Map.entry("февраля", 2), Map.entry("марта", 3),
            Map.entry("апреля", 4), Map.entry("мая", 5), Map.entry("июня", 6),
            Map.entry("июля", 7), Map.entry("августа", 8), Map.entry("сентября", 9),
            Map.entry("октября", 10), Map.entry("ноября", 11), Map.entry("декабря", 12));

    private static final Set<String> MONTH_NAMES = Set.of(
            "январь", "февраль", "март", "апрель", "май", "июнь", "июль",
            "август", "сентябрь", "октябрь", "ноябрь", "декабрь",
            "января", "февраля", "марта", "апреля", "мая", "июня", "июля",
            "августа", "сентября", "октября", "ноября", "декабря");

    // Left- and right-column tokens this close vertically belong to one visual line
    // (a title's merchant text and its amount share the same y).
    private static final float ROW_GAP = 0.01f;
    // A details line sits at most this far below its title; a positive amount
    // closer than this is cashback (discarded), farther is a new income operation.
    private static final float DETAILS_MAX_GAP = 0.03f;

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

        String cardNumber = null;
        LocalDate currentDate = null;
        State state = State.EXPECT_TITLE;
        Row current = null;
        List<Row> rows = new ArrayList<>();
        List<String> unrecognized = new ArrayList<>();
        StringBuilder orphan = new StringBuilder();

        for (VisualRow vr : groupRows(sorted)) {
            String text = String.join(" ", vr.texts).trim();

            if (cardNumber == null && isCardLine(text)) {
                cardNumber = maskedNumber(text);
                continue;
            }
            // Amount-only row above the first date header is the card balance
            // ("50 000 ₽") — discard it. It has no sign, so it is never credit.
            if (currentDate == null && vr.hasAmount() && vr.texts.isEmpty() && !vr.credit) {
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
                } else {
                    appendOrphan(orphan, text);
                }
            } else { // EXPECT_DETAILS
                boolean near = current != null && vr.y - current.y <= DETAILS_MAX_GAP;
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
    private static List<VisualRow> groupRows(List<OcrLine> sorted) {
        List<VisualRow> rows = new ArrayList<>();
        VisualRow current = null;
        for (OcrLine line : sorted) {
            if (isNoise(line.text())) {
                continue; // icons, clocks, "вы потратили", nav labels
            }
            if (current == null || Math.abs(line.y() - current.y) > ROW_GAP) {
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
        // When no date header was recognised yet (e.g. the very first group's
        // header was OCR'd poorly), fall back to "today" rather than emitting a
        // null date that would crash downstream validation.
        return currentDate != null ? currentDate : referenceDate;
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
        Path tmp = null;
        try {
            BufferedImage img;
            try (ByteArrayInputStream in = new ByteArrayInputStream(imageBytes)) {
                img = ImageIO.read(in);
            }
            if (img == null) {
                throw new StatementParseException("Не удалось прочитать изображение");
            }
            int width = img.getWidth();
            int height = img.getHeight();

            BufferedImage sharp = sharpen(img);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(sharp, "png", png);
            tmp = Files.createTempFile("screenshot", ".png");
            Files.write(tmp, png.toByteArray());

            Process process = new ProcessBuilder(TESSERACT, tmp.toString(), "stdout", "-l", "rus+eng", "tsv")
                    .redirectErrorStream(true)
                    .start();
            String tsv = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = process.waitFor();
            if (code != 0) {
                throw new StatementParseException("OCR не выполнен (tesseract, код " + code + ")");
            }
            return parseTsv(tsv, width, height);
        } catch (IOException e) {
            throw new StatementParseException("Ошибка OCR: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StatementParseException("OCR прерван");
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
     * Converts Tesseract's word-level TSV into {@link OcrLine}s. Each Tesseract
     * line is anchored on its currency marker ({@code ₽} or {@code "руб"}): the
     * numeric tokens immediately left of the marker are the amount, everything
     * left of those is the merchant/detail text. A signed token terminates the
     * amount walk, so a merchant number ("Магазин № 24") is never mistaken for
     * part of the amount.
     */
    static List<OcrLine> parseTsv(String tsv, int width, int height) {
        Map<String, List<Word>> wordsByLine = new LinkedHashMap<>();
        for (String raw : tsv.split("\n")) {
            String[] c = raw.split("\t", -1);
            if (c.length < 12 || !"5".equals(c[0])) {
                continue; // word level only
            }
            String key = c[1] + "|" + c[2] + "|" + c[3] + "|" + c[4];
            float x = width > 0 ? parseFloat(c[6]) / width : parseFloat(c[6]);
            float y = height > 0 ? parseFloat(c[7]) / height : parseFloat(c[7]);
            wordsByLine.computeIfAbsent(key, k -> new ArrayList<>()).add(new Word(c[11], x, y));
        }

        List<OcrLine> lines = new ArrayList<>();
        for (List<Word> words : wordsByLine.values()) {
            words.sort(Comparator.comparingDouble(w -> w.x));

            int anchor = findCurrencyAnchor(words);
            int amountStart = anchor < 0 ? words.size() : anchor;
            if (anchor >= 0) {
                // Walk left collecting the amount; a signed token marks its start.
                amountStart = anchor;
                while (amountStart > 0) {
                    String w = words.get(amountStart - 1).text;
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
                    Word w = words.get(i);
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
                    Word w = words.get(i);
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

    private static BigDecimal amountValue(String text) {
        String digits = text == null ? "" : text.replaceAll("[^\\d.,]", "");
        digits = digits.replace(',', '.');
        return digits.isEmpty() ? BigDecimal.ZERO : new BigDecimal(digits);
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
        // Nav / chrome labels.
        if (t.equals("главный") || t.equals("главная") || t.equals("история")
                || t.equals("платежи") || t.equals("чаты") || t.equals("оформить")) {
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

    private static float parseFloat(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private enum State { EXPECT_TITLE, EXPECT_DETAILS }

    /** A single Tesseract word with normalised coordinates. */
    private static final class Word {
        final String text;
        final float x;
        final float y;

        Word(String text, float x, float y) {
            this.text = text;
            this.x = x;
            this.y = y;
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
