package ru.sviridov.wallet.parser;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ScreenshotParserTest {

    private static final LocalDate REF = LocalDate.of(2026, 10, 31);

    private final ScreenshotParser parser = new ScreenshotParser();

    @Test
    void parsesCardNumberAmountsAndOperations() {
        List<OcrLine> lines = List.of(
                // status bar + header + nav noise
                new OcrLine("22:40", 0.05f, 0.01f),
                new OcrLine("81", 0.90f, 0.01f),
                new OcrLine("Главный", 0.05f, 0.04f),
                new OcrLine("Банк Карта • 4242", 0.05f, 0.07f),
                new OcrLine("Вы потратили 3 589 ₽", 0.05f, 0.10f),
                new OcrLine("История", 0.05f, 0.12f),
                new OcrLine("Октябрь", 0.05f, 0.15f),
                // Сегодня → REF: two 2-line blocks (title + details)
                new OcrLine("Сегодня", 0.05f, 0.20f),
                new OcrLine("Магазин", 0.20f, 0.24f),
                new OcrLine("-1 234,56 ₽", 0.75f, 0.245f),
                new OcrLine("Продукты", 0.20f, 0.26f),   // details line
                new OcrLine("Зарплата", 0.20f, 0.32f),
                new OcrLine("+5 000 ₽", 0.75f, 0.325f),
                new OcrLine("Зачисление", 0.20f, 0.34f),  // details line
                // Вчера → REF-1
                new OcrLine("Вчера", 0.05f, 0.40f),
                new OcrLine("Такси", 0.20f, 0.44f),
                new OcrLine("-500 ₽", 0.75f, 0.445f),
                new OcrLine("Поездка", 0.20f, 0.46f),     // details line
                // 29 октября → explicit date, title with no details line
                new OcrLine("29 октября", 0.05f, 0.52f),
                new OcrLine("Кафе", 0.20f, 0.56f),
                new OcrLine("-820,50 ₽", 0.75f, 0.565f),
                // 28 октября → explicit date, title with no details line
                new OcrLine("28 октября", 0.05f, 0.64f),
                new OcrLine("Аптека", 0.20f, 0.68f),
                new OcrLine("-1 150 ₽", 0.75f, 0.685f),
                // bottom nav noise
                new OcrLine("Главный", 0.05f, 0.95f),
                new OcrLine("История", 0.35f, 0.95f),
                new OcrLine("Платежи", 0.65f, 0.95f),
                new OcrLine("Чаты", 0.90f, 0.95f));

        ParsedStatement st = parser.parseLines(lines, REF);

        assertThat(st.cardNumber()).isEqualTo("••••4242");
        assertThat(st.rowCountRaw()).isEqualTo(5);
        assertThat(st.rows()).hasSize(5);

        ParsedRow r1 = st.rows().get(0);
        assertThat(r1.date()).isEqualTo(REF);
        assertThat(r1.operation()).isEqualTo("Магазин Продукты");
        assertThat(r1.expense()).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(r1.income()).isNull();
        assertThat(r1.purpose()).isEmpty();

        ParsedRow r2 = st.rows().get(1);
        assertThat(r2.operation()).isEqualTo("Зарплата Зачисление");
        assertThat(r2.income()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(r2.expense()).isNull();

        ParsedRow r3 = st.rows().get(2);
        assertThat(r3.date()).isEqualTo(REF.minusDays(1));
        assertThat(r3.operation()).isEqualTo("Такси Поездка");

        ParsedRow r4 = st.rows().get(3);
        assertThat(r4.date()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(r4.operation()).isEqualTo("Кафе");

        ParsedRow r5 = st.rows().get(4);
        assertThat(r5.date()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(r5.operation()).isEqualTo("Аптека");
        assertThat(r5.expense()).isEqualByComparingTo(new BigDecimal("1150.00"));
    }

    @Test
    void keepsPurposeEmptyAndSkipsNoise() {
        List<OcrLine> lines = List.of(
                new OcrLine("Банк Карта • 4242", 0.05f, 0.05f),
                new OcrLine("Сегодня", 0.05f, 0.20f),
                new OcrLine("Кофейня", 0.20f, 0.24f),
                new OcrLine("-180 ₽", 0.75f, 0.245f),
                new OcrLine("+5", 0.85f, 0.25f), // cashback (no ₽, right side) → ignored
                new OcrLine("→", 0.02f, 0.24f));  // icon → ignored

        ParsedStatement st = parser.parseLines(lines, REF);

        assertThat(st.rows()).hasSize(1);
        assertThat(st.rows().get(0).operation()).isEqualTo("Кофейня");
        assertThat(st.rows().get(0).purpose()).isEmpty();
        assertThat(st.rows().get(0).expense()).isEqualByComparingTo(new BigDecimal("180.00"));
    }

    @Test
    void discardsCashbackOnDetailsLine() {
        // A positive amount on the details line (cashback) is discarded; a
        // positive amount on a separate title line is real income.
        List<OcrLine> lines = List.of(
                new OcrLine("Банк Карта • 4242", 0.05f, 0.05f),
                new OcrLine("Сегодня", 0.05f, 0.20f),
                new OcrLine("Магазин", 0.20f, 0.24f),
                new OcrLine("-100 ₽", 0.75f, 0.245f),
                new OcrLine("Продукты", 0.20f, 0.26f),
                new OcrLine("+10 ₽", 0.75f, 0.265f), // cashback on details → discarded
                new OcrLine("Зарплата", 0.20f, 0.32f),
                new OcrLine("+25 000 ₽", 0.75f, 0.325f),
                new OcrLine("Зачисление", 0.20f, 0.34f));

        ParsedStatement st = parser.parseLines(lines, REF);

        assertThat(st.rows()).hasSize(2);
        assertThat(st.rows().get(0).operation()).isEqualTo("Магазин Продукты");
        assertThat(st.rows().get(0).expense()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(st.rows().get(0).income()).isNull();
        assertThat(st.rows().get(1).operation()).isEqualTo("Зарплата Зачисление");
        assertThat(st.rows().get(1).income()).isEqualByComparingTo(new BigDecimal("25000.00"));
        assertThat(st.rows().get(1).expense()).isNull();
        assertThat(st.rowCountRaw()).isEqualTo(2);
    }

    @Test
    void parseTsvSplitsMergedMerchantAmountLine() {
        // Tesseract often reports the merchant and the amount on the SAME line;
        // parseTsv anchors on the currency marker and splits the amount off to
        // its own line (right-edge x = the marker's x).
        String tsv = String.join("\n",
                "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext",
                "5\t1\t1\t1\t1\t1\t200\t100\t100\t20\t95\tМагазин",
                "5\t1\t1\t1\t1\t2\t700\t100\t50\t20\t95\t-2",
                "5\t1\t1\t1\t1\t3\t720\t100\t60\t20\t95\t980,74",
                "5\t1\t1\t1\t1\t4\t780\t100\t20\t20\t95\t\u20BD");

        List<OcrLine> lines = ScreenshotParser.parseTsv(tsv, 1000, 800);

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).text()).isEqualTo("Магазин");
        assertThat(lines.get(0).x()).isCloseTo(0.2f, offset(0.001f));
        assertThat(lines.get(1).text()).isEqualTo("-2 980,74 \u20BD");
        assertThat(lines.get(1).x()).isCloseTo(0.78f, offset(0.001f));
    }

    @Test
    void detectsBareNegativeExpenseButSkipsBarePositiveCashback() {
        // When OCR drops the currency marker, a marker-less negative is a real
        // expense, but a marker-less positive is cashback (e.g. "+5"/"+180").
        List<OcrLine> lines = List.of(
                new OcrLine("Банк Карта • 4242", 0.05f, 0.05f),
                new OcrLine("Сегодня", 0.05f, 0.20f),
                new OcrLine("Аптека", 0.20f, 0.24f),
                new OcrLine("-700", 0.75f, 0.245f), // bare negative → expense
                new OcrLine("+5", 0.85f, 0.245f));  // bare positive → cashback, skipped

        ParsedStatement st = parser.parseLines(lines, REF);

        assertThat(st.rows()).hasSize(1);
        assertThat(st.rows().get(0).operation()).isEqualTo("Аптека");
        assertThat(st.rows().get(0).expense()).isEqualByComparingTo(new BigDecimal("700.00"));
    }

    @Test
    void keepsRowWithZeroAmountWhenAmountIsMissing() {
        // A line with text but no amount is an operation with an unknown amount:
        // it is kept with 0 and a warning downstream, because deleting an extra row
        // in the wizard is cheaper than re-typing a missing operation. The
        // status-bar clock is still treated as noise.
        List<OcrLine> lines = List.of(
                new OcrLine("22:40", 0.05f, 0.01f), // status-bar clock → noise
                new OcrLine("Банк Карта • 4242", 0.05f, 0.05f),
                new OcrLine("Сегодня", 0.05f, 0.20f),
                new OcrLine("Магазин", 0.20f, 0.24f),    // no amount
                new OcrLine("Продукты", 0.20f, 0.26f),   // its details line
                new OcrLine("Кофейня", 0.20f, 0.40f),
                new OcrLine("-450 руб", 0.75f, 0.405f));

        ParsedStatement st = parser.parseLines(lines, REF);

        assertThat(st.rows()).hasSize(2);
        assertThat(st.rows().get(0).operation()).isEqualTo("Магазин Продукты");
        assertThat(st.rows().get(0).expense()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(st.rows().get(1).operation()).isEqualTo("Кофейня");
        assertThat(st.rows().get(1).expense()).isEqualByComparingTo(new BigDecimal("450"));
        assertThat(st.unrecognizedRows()).isEmpty();
    }

    @Test
    void detectsCardNumberWithoutBulletSeparator() {
        // Tesseract does not reliably recognise the "•" glyph, so the card line
        // must be detected from "Карта" + the 4-digit tail alone.
        List<OcrLine> lines = List.of(
                new OcrLine("Банк Карта 4242", 0.05f, 0.05f),
                new OcrLine("Сегодня", 0.05f, 0.20f),
                new OcrLine("Кофейня", 0.20f, 0.24f),
                new OcrLine("-180 ₽", 0.75f, 0.245f));

        ParsedStatement st = parser.parseLines(lines, REF);

        assertThat(st.cardNumber()).isEqualTo("••••4242");
        assertThat(st.rows()).hasSize(1);
    }

    @Test
    void parseTsvKeepsLongMerchantSuffixOutOfAmount() {
        // A long merchant name whose tail ("Inc.") sits where the old fixed
        // amount column started must stay in the text, not pollute the amount.
        String tsv = String.join("\n",
                "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext",
                "5\t1\t1\t1\t1\t1\t50\t100\t60\t20\t95\tAcme",
                "5\t1\t1\t1\t1\t2\t110\t100\t50\t20\t95\tGroup",
                "5\t1\t1\t1\t1\t3\t160\t100\t30\t20\t95\tInc.",
                "5\t1\t1\t1\t1\t4\t430\t100\t40\t20\t95\t-2",
                "5\t1\t1\t1\t1\t5\t470\t100\t80\t20\t95\t980,74",
                "5\t1\t1\t1\t1\t6\t550\t100\t20\t20\t95\t\u20BD");
        List<OcrLine> lines = ScreenshotParser.parseTsv(tsv, 1000, 800);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).text()).isEqualTo("Acme Group Inc.");
        assertThat(lines.get(1).text()).isEqualTo("-2 980,74 \u20BD");
    }

    @Test
    void unsignedCurrencyAmountIsAcceptedAsExpense() {
        // OCR drops the sign; a currency-marked unsigned number is still an expense.
        List<OcrLine> lines = List.of(
                new OcrLine("Банк Карта • 4242", 0.05f, 0.05f),
                new OcrLine("Сегодня", 0.05f, 0.20f),
                new OcrLine("Кофейня", 0.20f, 0.24f),
                new OcrLine("450 ₽", 0.75f, 0.245f));
        ParsedStatement st = parser.parseLines(lines, REF);
        assertThat(st.rows()).hasSize(1);
        assertThat(st.rows().get(0).operation()).isEqualTo("Кофейня");
        assertThat(st.rows().get(0).expense()).isEqualByComparingTo(new BigDecimal("450.00"));
        assertThat(st.rows().get(0).income()).isNull();
    }

    @Test
    void skipsBalanceAmountAboveFirstDateHeader() {
        // The card balance ("50 000 ₽") sits above the first date header and has
        // no sign — it must not become the first operation.
        List<OcrLine> lines = List.of(
                new OcrLine("Банк Карта • 4242", 0.05f, 0.05f),
                new OcrLine("50 000 ₽", 0.75f, 0.07f),  // balance → skipped
                new OcrLine("Сегодня", 0.05f, 0.20f),
                new OcrLine("Кофейня", 0.20f, 0.24f),
                new OcrLine("-180 ₽", 0.75f, 0.245f));
        ParsedStatement st = parser.parseLines(lines, REF);
        assertThat(st.rows()).hasSize(1);
        assertThat(st.rows().get(0).operation()).isEqualTo("Кофейня");
        assertThat(st.rows().get(0).expense()).isEqualByComparingTo(new BigDecimal("180.00"));
        assertThat(st.rowCountRaw()).isEqualTo(1);
    }

    @Test
    void fallsBackToReferenceDateWhenNoHeaderSeen() {
        // An operation above the first (unrecognised) date header must not get a
        // null date — it falls back to "today" so downstream validation survives.
        List<OcrLine> lines = List.of(
                new OcrLine("Кофейня", 0.20f, 0.10f),
                new OcrLine("-180 ₽", 0.75f, 0.105f));
        ParsedStatement st = parser.parseLines(lines, REF);
        assertThat(st.rows()).hasSize(1);
        assertThat(st.rows().get(0).date()).isEqualTo(REF);
        assertThat(st.rows().get(0).expense()).isEqualByComparingTo(new BigDecimal("180.00"));
    }

    /**
     * Full pipeline (image → Tesseract OCR → parse) against the sanitised
     * fixture. Skipped when Tesseract is not installed (e.g. local non-Docker run).
     */
    @Test
    void parsesSanitizedScreenshotImageEndToEnd() throws Exception {
        assumeTrue(tesseractAvailable(), "tesseract not installed");

        byte[] image = getClass().getResourceAsStream("/screenshot_test.png").readAllBytes();
        ParsedStatement st = parser.parse(image);

        assertThat(st.cardNumber()).isEqualTo("••••4242");
        assertThat(st.rows()).hasSize(8);
        // "Сегодня" group comes first and resolves to today
        assertThat(st.rows().get(0).date()).isEqualTo(LocalDate.now());
        // "Вчера" group resolves to yesterday
        assertThat(st.rows().get(2).date()).isEqualTo(LocalDate.now().minusDays(1));
        assertThat(st.rows().get(2).expense()).isEqualByComparingTo(new BigDecimal("320.00"));
    }

    @Test
    void sharpenPreservesDimensionsAndReturnsGrayscale() {
        BufferedImage src = new BufferedImage(120, 60, BufferedImage.TYPE_INT_RGB);
        BufferedImage out = ScreenshotParser.sharpen(src);
        assertThat(out).isNotNull();
        assertThat(out.getWidth()).isEqualTo(120);
        assertThat(out.getHeight()).isEqualTo(60);
        assertThat(out.getType()).isEqualTo(BufferedImage.TYPE_BYTE_GRAY);
    }

    @Test
    void parseTsvAnchorsAmountOnCurrencyMarkerKeepsMerchantNumber() {
        // The amount is anchored on ₽: the walk-left collects "980,74" then stops
        // at the signed "-2" token, so the merchant number "24" (in "Магазин № 24")
        // is never mistaken for part of the amount — regardless of its x position.
        String tsv = String.join("\n",
                "5\t1\t1\t1\t1\t1\t50\t600\t120\t40\t95\tМагазин",
                "5\t1\t1\t1\t1\t2\t120\t600\t30\t40\t95\t№",
                "5\t1\t1\t1\t1\t3\t150\t600\t20\t40\t95\t24",
                "5\t1\t1\t1\t1\t4\t420\t600\t40\t40\t95\t-2",
                "5\t1\t1\t1\t1\t5\t460\t600\t80\t40\t95\t980,74",
                "5\t1\t1\t1\t1\t6\t510\t600\t30\t40\t95\t₽");
        List<OcrLine> lines = ScreenshotParser.parseTsv(tsv, 1000, 2000);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).text()).isEqualTo("Магазин № 24");
        assertThat(lines.get(1).text()).isEqualTo("-2 980,74 ₽");
        assertThat(lines.get(1).x()).isEqualTo(0.51f, offset(0.01f));
    }

    @Test
    void parseTsvFindsCurrencyAttachedToAmount() {
        // Tesseract often glues "₽" onto the number ("980,74₽"), leaving no separate
        // marker word. The anchor must still be found, otherwise the whole line
        // degrades into text and the operation is lost.
        String tsv = String.join("\n",
                "5\t1\t1\t1\t1\t1\t50\t600\t120\t40\t95\tМагазин",
                "5\t1\t1\t1\t1\t2\t420\t600\t40\t40\t95\t-2",
                "5\t1\t1\t1\t1\t3\t460\t600\t90\t40\t95\t980,74₽");
        List<OcrLine> lines = ScreenshotParser.parseTsv(tsv, 1000, 2000);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).text()).isEqualTo("Магазин");
        assertThat(lines.get(1).text()).isEqualTo("-2 980,74₽");
    }

    @Test
    void parseTsvReClustersMergedLinesByYAndKeepsBothAmounts() {
        // Tesseract merges two adjacent visual lines into ONE "line" key (the
        // icon gutter confuses its column detection), but their y coordinates
        // differ. Re-clustering by y keeps each amount, so both operations survive.
        String tsv = String.join("\n",
                "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext",
                "5\t1\t1\t1\t1\t1\t50\t100\t120\t20\t95\tМагазин",
                "5\t1\t1\t1\t1\t2\t430\t100\t40\t20\t95\t-2",
                "5\t1\t1\t1\t1\t3\t470\t100\t90\t20\t95\t980,74₽",
                "5\t1\t1\t1\t1\t4\t50\t130\t120\t20\t95\tПрочие товары",
                "5\t1\t1\t1\t1\t5\t430\t160\t40\t20\t95\t-3",
                "5\t1\t1\t1\t1\t6\t470\t160\t70\t20\t95\t000₽",
                "5\t1\t1\t1\t1\t7\t50\t160\t120\t20\t95\tМежду счетами");

        List<OcrLine> lines = ScreenshotParser.parseTsv(tsv, 1000, 800);

        assertThat(lines.stream().map(OcrLine::text)).containsExactly(
                "Магазин", "-2 980,74₽", "Прочие товары", "Между счетами", "-3 000₽");
    }

    @Test
    void parseLinesParsesAttachedRubleMarkerAmount() {
        // "511р" (no space) is a common OCR output for "511 ₽". The whole number
        // must be kept as 511, not read as "5.00" or split into "5"+"11".
        List<OcrLine> lines = List.of(
                new OcrLine("Сегодня", 0.05f, 0.10f),
                new OcrLine("Магазин", 0.05f, 0.20f),
                new OcrLine("511р", 0.90f, 0.20f));
        ParsedStatement st = parser.parseLines(lines, REF);
        assertThat(st.rows()).hasSize(1);
        assertThat(st.rows().get(0).operation()).isEqualTo("Магазин");
        assertThat(st.rows().get(0).expense()).isEqualByComparingTo(new BigDecimal("511"));
    }

    @Test
    void rejoinedAmountsDoNotCollapseAdjacentOperations() {
        List<OcrLine> lines = List.of(
                new OcrLine("Магазин № 24", 0.05f, 0.30f),
                new OcrLine("-2 980,74 ₽", 0.51f, 0.30f),
                new OcrLine("Продукты", 0.05f, 0.32f),   // details, gap 0.02 from title
                new OcrLine("Перевод себе", 0.05f, 0.38f),
                new OcrLine("-3 000 ₽", 0.51f, 0.38f),
                new OcrLine("На накопительный счёт", 0.05f, 0.40f)); // details
        ParsedStatement st = parser.parseLines(lines, REF);
        assertThat(st.rows()).hasSize(2);
        assertThat(st.rows().get(0).operation()).isEqualTo("Магазин 24 Продукты");
        assertThat(st.rows().get(0).expense()).isEqualByComparingTo(new BigDecimal("2980.74"));
        assertThat(st.rows().get(1).operation()).isEqualTo("Перевод себе На накопительный счёт");
        assertThat(st.rows().get(1).expense()).isEqualByComparingTo(new BigDecimal("3000.00"));
        assertThat(st.unrecognizedRows()).isEmpty();
    }

    @Test
    void stripsSpecialCharactersFromOperationKeepingLettersDigitsAndPeriod() {
        // Final cleanup: only RU/EN letters, digits and "." survive; every other
        // character (quotes, parens, №, em-dash, arrow) collapses to a space.
        List<OcrLine> lines = List.of(
                new OcrLine("Кафе «Вкусно» (сеть) №7", 0.05f, 0.30f),
                new OcrLine("-450,50 ₽", 0.51f, 0.30f),
                new OcrLine("Acme Group Inc.", 0.05f, 0.32f));
        ParsedStatement st = parser.parseLines(lines, REF);
        assertThat(st.rows()).hasSize(1);
        assertThat(st.rows().get(0).operation()).isEqualTo("Кафе Вкусно сеть 7 Acme Group Inc.");
    }

    @Test
    void segmentationSplitsOperationsAtBlankBands() {
        // Dark 540×400 image: two 2-line operations separated by a large blank band.
        int w = 540;
        int h = 400;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.WHITE);
        int x0 = 100; // text column only (icon gutter left dark)
        // operation 1: title 50-70, gap 9, details 80-95
        g.fillRect(x0, 50, w - x0, 21);
        g.fillRect(x0, 80, w - x0, 16);
        // operation 2: title 130-150, gap 9, details 160-175
        g.fillRect(x0, 130, w - x0, 21);
        g.fillRect(x0, 160, w - x0, 16);
        g.dispose();

        List<ScreenshotParser.Unit> units = ScreenshotParser.segmentUnits(img);

        assertThat(units).hasSize(2);
        assertThat(units.get(0).y0).isEqualTo(50);
        assertThat(units.get(0).y1).isEqualTo(95);
        assertThat(units.get(1).y0).isEqualTo(130);
        assertThat(units.get(1).y1).isEqualTo(175);
    }

    private static boolean tesseractAvailable() {
        try {
            Process p = new ProcessBuilder("tesseract", "--version").redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
