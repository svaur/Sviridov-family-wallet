package ru.sviridov.wallet.parser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PdfBoxStatementParserTest {

    private final PdfBoxStatementParser parser = new PdfBoxStatementParser();

    private byte[] loadFixture() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("TEST_PDF.pdf")) {
            assertThat(in).isNotNull();
            return in.readAllBytes();
        }
    }

    private byte[] loadMultiPageFixture() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("тестовая_выписка_отп.pdf")) {
            assertThat(in).isNotNull();
            return in.readAllBytes();
        }
    }

    @Test
    void parsesHeaderMetadata() throws Exception {
        ParsedStatement s = parser.parse(loadFixture());

        assertThat(s.periodFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(s.periodTo()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(s.cardNumber()).isEqualTo("2202XXXXXXXX1234");
        assertThat(s.contract()).isEqualTo("1234567890");
        assertThat(s.account()).isEqualTo("40817810000000000000");
    }

    @Test
    void parsesAllRowsWithCorrectTypes() throws Exception {
        ParsedStatement s = parser.parse(loadFixture());

        assertThat(s.rowCountRaw()).isEqualTo(12);
        assertThat(s.rows()).hasSize(12);
        assertThat(s.unrecognizedRows()).isEmpty();

        ParsedRow first = s.rows().get(0);
        assertThat(first.rowNumber()).isEqualTo(1);
        assertThat(first.date()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(first.operation()).isEqualTo("Оплата через СБП ООО РОМАШКА");
        assertThat(first.type()).isEqualTo("debit");
        assertThat(first.amount()).isEqualByComparingTo(new BigDecimal("1234.50"));
        assertThat(first.income()).isNull();
        assertThat(first.purpose()).isEqualTo("Списание по транзакции. Безналичная оплата покупки");

        ParsedRow credit = s.rows().get(5);
        assertThat(credit.rowNumber()).isEqualTo(6);
        assertThat(credit.type()).isEqualTo("credit");
        assertThat(credit.expense()).isNull();
        assertThat(credit.income()).isEqualByComparingTo(new BigDecimal("120000.00"));
        assertThat(credit.operation()).isEqualTo("Зачисление зарплаты");

        ParsedRow transfer = s.rows().get(2);
        assertThat(transfer.operation()).isEqualTo("Перевод средств");
        assertThat(transfer.purpose()).isEqualTo("Между своими счетами");
    }

    @Test
    void rejectsGarbageInput() {
        assertThrows(StatementParseException.class, () -> parser.parse(new byte[]{1, 2, 3, 4}));
    }

    @Test
    void parsesAllRowsAcrossPages() throws Exception {
        ParsedStatement s = parser.parse(loadMultiPageFixture());

        assertThat(s.periodFrom()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(s.periodTo()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(s.cardNumber()).isEqualTo("2201XXXXXXXX7777");
        assertThat(s.contract()).isEqualTo("1234567890");
        assertThat(s.account()).isEqualTo("40817810000000007777");

        // Regression: the fixture has 3 pages; page-relative Y coordinates must not merge
        // rows from different pages (previously produced ~11 merged rows instead of 19).
        assertThat(s.rowCountRaw()).isEqualTo(19);
        assertThat(s.rows()).hasSize(19);
        assertThat(s.unrecognizedRows()).isEmpty();
        assertThat(s.rows()).extracting(ParsedRow::rowNumber)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19);

        // No page header may leak into a row's operation/purpose text.
        assertThat(s.rows()).allSatisfy(row -> {
            assertThat(row.operation()).doesNotContain("Карта:");
            assertThat(row.purpose()).doesNotContain("Карта:");
        });
    }
}
