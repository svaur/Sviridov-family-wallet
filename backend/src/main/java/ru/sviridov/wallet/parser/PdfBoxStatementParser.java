package ru.sviridov.wallet.parser;

import org.springframework.stereotype.Component;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDFBox implementation of {@link StatementParser} for the bank "Счет-выписка"
 * format:
 *
 * <pre>
 * За период с: 01.09.2026 по: 30.09.2026
 * Карта: 2202XXXXXXXX1234
 * Договор: 1234567890
 * Счет: 40817810000000000000 (руб.)
 * Номер | Дата операции | Операция | Расход | Приход | Назначение платежа
 * </pre>
 *
 * Debit/credit is determined by the x-position of the amount token relative to
 * the "Расход" and "Приход" column headers, mirroring the bank's layout.
 */
@Component
public class PdfBoxStatementParser implements StatementParser {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Pattern P_PERIOD = Pattern.compile("За период с:\\s*(\\d{2}\\.\\d{2}\\.\\d{4})\\s*по:\\s*(\\d{2}\\.\\d{2}\\.\\d{4})");
    private static final Pattern P_CARD = Pattern.compile("Карта:\\s*(\\S+)");
    private static final Pattern P_CONTRACT = Pattern.compile("Договор:\\s*(\\S+)");
    private static final Pattern P_ACCOUNT = Pattern.compile("Счет:\\s*(\\d+)");
    private static final Pattern P_ROW_NUM = Pattern.compile("^\\d{1,4}$");
    private static final Pattern P_DATE = Pattern.compile("^\\d{2}\\.\\d{2}\\.\\d{4}$");
    private static final Pattern P_MONEY = Pattern.compile("^\\d+(?:[.,]\\d{2})$");
    /** Max horizontal distance between an amount and its "Расход"/"Приход" column header. */
    private static final float COLUMN_TOLERANCE = 60f;
    /** Tokens whose y differs by less than this belong to the same visual line. */
    private static final float LINE_TOLERANCE = 5f;
    /**
     * The footer ("10 18183.86 14300.0") starts with a number too, so a line only
     * begins a row when its first token is close to the "Номер" header column.
     */
    private static final float ROW_NUMBER_TOLERANCE = 30f;
    /**
     * A line is a continuation of the previous row's "Назначение платежа" when all
     * its tokens sit at or right of the purpose column (wrapped long purposes).
     */
    private static final float PURPOSE_TOLERANCE = 15f;

    @Override
    public String version() {
        return "pdfbox-1.0";
    }

    @Override
    public ParsedStatement parse(byte[] pdfBytes) throws StatementParseException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            String fullText = extractText(doc);

            LocalDate periodFrom = parseDate(regexGroup(fullText, P_PERIOD, 1));
            LocalDate periodTo = parseDate(regexGroup(fullText, P_PERIOD, 2));
            String cardNumber = regexGroup(fullText, P_CARD, 1);
            String contract = regexGroup(fullText, P_CONTRACT, 1);
            String account = regexGroup(fullText, P_ACCOUNT, 1);

            List<Token> tokens = extractTokens(doc);
            Float expenseX = null;
            Float incomeX = null;
            Float numberX = null;
            Float purposeX = null;
            for (Token t : tokens) {
                if (expenseX == null && "Расход".equals(t.text())) {
                    expenseX = t.x();
                } else if (incomeX == null && "Приход".equals(t.text())) {
                    incomeX = t.x();
                } else if (numberX == null && "Номер".equals(t.text())) {
                    numberX = t.x();
                } else if (purposeX == null && "Назначение".equals(t.text())) {
                    purposeX = t.x();
                }
            }
            if (expenseX == null || incomeX == null) {
                throw new StatementParseException("Не найдены колонки «Расход»/«Приход» в таблице выписки");
            }
            float rowNumberColumn = numberX != null ? numberX : 68f;
            float purposeColumn = purposeX != null ? purposeX : expenseX + 256f;

            List<ParsedRow> rows = new ArrayList<>();
            List<String> unrecognized = new ArrayList<>();
            int rowCountRaw = 0;
            for (List<Token> line : groupByY(tokens)) {
                if (line.isEmpty()) {
                    continue;
                }
                line.sort(Comparator.comparingDouble(Token::x));
                Token first = line.get(0);
                boolean isRowStart = P_ROW_NUM.matcher(first.text()).matches()
                        && Math.abs(first.x() - rowNumberColumn) <= ROW_NUMBER_TOLERANCE;
                if (isRowStart) {
                    rowCountRaw++;
                    ParsedRow row = parseRow(line, expenseX, incomeX);
                    if (row == null) {
                        unrecognized.add("Строка " + first.text());
                    } else {
                        rows.add(row);
                    }
                } else if (!rows.isEmpty() && isPurposeContinuation(line, purposeColumn)) {
                    // Wrapped "Назначение платежа" continuation of the previous row.
                    ParsedRow prev = rows.remove(rows.size() - 1);
                    StringBuilder extra = new StringBuilder();
                    for (Token t : line) {
                        if (extra.length() > 0) {
                            extra.append(' ');
                        }
                        extra.append(t.text());
                    }
                    String purpose = prev.purpose() == null || prev.purpose().isBlank()
                            ? extra.toString()
                            : prev.purpose() + " " + extra;
                    rows.add(new ParsedRow(prev.rowNumber(), prev.date(), prev.operation(),
                            prev.expense(), prev.income(), purpose));
                }
                // else: header, footer or signature line — ignore
            }

            return new ParsedStatement(periodFrom, periodTo, cardNumber, contract, account,
                    rowCountRaw, rows, unrecognized);
        } catch (IOException e) {
            throw new StatementParseException("Не удалось прочитать PDF: " + e.getMessage(), e);
        }
    }

    private static ParsedRow parseRow(List<Token> line, float expenseX, float incomeX) {
        Token rowNumTok = line.get(0);
        int rowNumber;
        try {
            rowNumber = Integer.parseInt(rowNumTok.text());
        } catch (NumberFormatException e) {
            return null;
        }

        Token dateTok = null;
        for (Token t : line) {
            if (P_DATE.matcher(t.text()).matches()) {
                dateTok = t;
                break;
            }
        }
        if (dateTok == null) {
            return null;
        }
        LocalDate date = parseDate(dateTok.text());
        if (date == null) {
            return null;
        }

        Token amountTok = null;
        String column = null;
        float bestDistance = Float.MAX_VALUE;
        for (Token t : line) {
            if (!P_MONEY.matcher(t.text()).matches()) {
                continue;
            }
            float dExp = Math.abs(t.x() - expenseX);
            float dInc = Math.abs(t.x() - incomeX);
            float distance = Math.min(dExp, dInc);
            if (distance > COLUMN_TOLERANCE) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                amountTok = t;
                column = dExp <= dInc ? "debit" : "credit";
            }
        }
        if (amountTok == null) {
            return null;
        }

        BigDecimal amount = new BigDecimal(amountTok.text().replace(',', '.'));
        BigDecimal expense = "debit".equals(column) ? amount : null;
        BigDecimal income = "credit".equals(column) ? amount : null;

        StringBuilder operation = new StringBuilder();
        StringBuilder purpose = new StringBuilder();
        for (Token t : line) {
            if (t == rowNumTok || t == dateTok || t == amountTok) {
                continue;
            }
            if (t.x() < amountTok.x() - 5f) {
                if (operation.length() > 0) {
                    operation.append(' ');
                }
                operation.append(t.text());
            } else if (t.x() > amountTok.x() + 5f) {
                if (purpose.length() > 0) {
                    purpose.append(' ');
                }
                purpose.append(t.text());
            }
        }

        return new ParsedRow(rowNumber, date, operation.toString(), expense, income, purpose.toString());
    }

    /**
     * True when every token of the line sits in the "Назначение платежа" column
     * (to the right of {@code purposeColumn}), i.e. it is a wrapped continuation
     * of the previous row's purpose rather than a standalone row/header/footer.
     */
    private static boolean isPurposeContinuation(List<Token> line, float purposeColumn) {
        for (Token t : line) {
            if (t.x() < purposeColumn - PURPOSE_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    private static String extractText(PDDocument doc) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        return stripper.getText(doc);
    }

    private static List<Token> extractTokens(PDDocument doc) throws IOException {
        TokenCollector collector = new TokenCollector();
        collector.setSortByPosition(true);
        collector.getText(doc);
        return collector.tokens;
    }

    /**
     * Groups tokens into visual lines. Tokens are grouped per page first: a
     * multi-page statement restarts its vertical coordinate system on every
     * page, so two unrelated rows can share the same {@code y} and must not be
     * merged into one line.
     */
    private static List<List<Token>> groupByY(List<Token> tokens) {
        List<Token> sorted = new ArrayList<>(tokens);
        sorted.sort(Comparator.comparingInt(Token::page)
                .thenComparingDouble(Token::y)
                .thenComparingDouble(Token::x));
        List<List<Token>> lines = new ArrayList<>();
        List<Token> current = new ArrayList<>();
        int currentPage = -1;
        float currentY = 0f;
        for (Token t : sorted) {
            boolean sameLine = !current.isEmpty()
                    && t.page() == currentPage
                    && Math.abs(t.y() - currentY) <= LINE_TOLERANCE;
            if (sameLine) {
                current.add(t);
            } else {
                if (!current.isEmpty()) {
                    lines.add(current);
                }
                current = new ArrayList<>();
                current.add(t);
                currentY = t.y();
                currentPage = t.page();
            }
        }
        if (!current.isEmpty()) {
            lines.add(current);
        }
        return lines;
    }

    private static String regexGroup(String text, Pattern pattern, int group) {
        if (text == null) {
            return null;
        }
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(group) : null;
    }

    private static LocalDate parseDate(String s) {
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s, DATE_FMT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private record Token(String text, float x, float y, int page) {
    }

    private static final class TokenCollector extends PDFTextStripper {
        final List<Token> tokens = new ArrayList<>();

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            if (textPositions == null || textPositions.isEmpty()) {
                super.writeString(text, textPositions);
                return;
            }
            float y = textPositions.get(0).getYDirAdj();
            int page = getCurrentPageNo();
            int n = Math.min(text.length(), textPositions.size());
            StringBuilder sb = new StringBuilder();
            Float x = null;
            for (int i = 0; i < n; i++) {
                char c = text.charAt(i);
                TextPosition tp = textPositions.get(i);
                if (Character.isWhitespace(c)) {
                    if (sb.length() > 0 && x != null) {
                        tokens.add(new Token(sb.toString(), x, y, page));
                    }
                    sb.setLength(0);
                    x = null;
                } else {
                    if (x == null) {
                        x = tp.getXDirAdj();
                    }
                    sb.append(c);
                }
            }
            if (sb.length() > 0 && x != null) {
                tokens.add(new Token(sb.toString(), x, y, page));
            }
            super.writeString(text, textPositions);
        }
    }
}
