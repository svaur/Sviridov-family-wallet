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
        assertThat(first.operation()).isEqualTo("Оплата через СБП");
        assertThat(first.type()).isEqualTo("debit");
        assertThat(first.amount()).isEqualByComparingTo(new BigDecimal("1234.50"));
        assertThat(first.income()).isNull();
        assertThat(first.purpose()).isEqualTo("ООО РОМАШКА");

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
}
