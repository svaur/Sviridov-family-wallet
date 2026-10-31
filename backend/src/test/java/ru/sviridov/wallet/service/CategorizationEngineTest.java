package ru.sviridov.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.sviridov.wallet.domain.Rule;
import ru.sviridov.wallet.domain.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CategorizationEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TextNormalizer normalizer = new TextNormalizer();
    private final CategorizationEngine engine = new CategorizationEngine(objectMapper, normalizer);

    @Test
    void operationContainsMatches() {
        Rule rule = rule("{\"operation_contains\":\"перевод\"}", 13L);
        assertThat(engine.categorize(tx("Перевод средств"), List.of(rule))).isEqualTo(13L);
    }

    @Test
    void operationContainsIsCaseInsensitive() {
        Rule rule = rule("{\"operation_contains\":\"ПЕРЕВОД\"}", 12L);
        assertThat(engine.categorize(tx("перевод средств"), List.of(rule))).isEqualTo(12L);
    }

    @Test
    void operationRegexMatchesCaseInsensitive() {
        Rule rule = rule("{\"operation_regex\":\"ПЕРЕВОД\"}", 14L);
        assertThat(engine.categorize(tx("Перевод средств"), List.of(rule))).isEqualTo(14L);
    }

    @Test
    void purposeContainsMatches() {
        Rule rule = rule("{\"purpose_contains\":\"аренд\"}", 15L);
        assertThat(engine.categorize(transfer("Оплата аренды квартиры"), List.of(rule))).isEqualTo(15L);
    }

    @Test
    void purposeContainsIsCaseInsensitive() {
        Rule rule = rule("{\"purpose_contains\":\"АРЕНД\"}", 16L);
        assertThat(engine.categorize(transfer("оплата аренды квартиры"), List.of(rule))).isEqualTo(16L);
    }

    @Test
    void purposeRegexMatchesCaseInsensitive() {
        Rule rule = rule("{\"purpose_regex\":\"АРЕНД\"}", 17L);
        assertThat(engine.categorize(transfer("Оплата аренды квартиры"), List.of(rule))).isEqualTo(17L);
    }

    @Test
    void amountRangeIsInclusive() {
        Rule rule = rule("{\"min\":100,\"max\":500}", 30L);
        assertThat(engine.categorize(tx("x", "500.00"), List.of(rule))).isEqualTo(30L);
        assertThat(engine.categorize(tx("x", "500.01"), List.of(rule))).isNull();
    }

    @Test
    void minOnlyMatches() {
        Rule rule = rule("{\"min\":100}", 31L);
        assertThat(engine.categorize(tx("x", "99.99"), List.of(rule))).isNull();
        assertThat(engine.categorize(tx("x", "100.00"), List.of(rule))).isEqualTo(31L);
    }

    @Test
    void dayRangeMatches() {
        Rule rule = rule("{\"day_from\":15,\"day_to\":20}", 40L);
        assertThat(engine.categorize(tx("x", "10.00", LocalDate.of(2026, 1, 15)), List.of(rule))).isEqualTo(40L);
        assertThat(engine.categorize(tx("x", "10.00", LocalDate.of(2026, 1, 20)), List.of(rule))).isEqualTo(40L);
        assertThat(engine.categorize(tx("x", "10.00", LocalDate.of(2026, 1, 14)), List.of(rule))).isNull();
        assertThat(engine.categorize(tx("x", "10.00", LocalDate.of(2026, 1, 21)), List.of(rule))).isNull();
    }

    @Test
    void combinedConditionsMatchAll() {
        // amount 5000-10000 AND day 15-20 (the motivating example)
        Rule rule = rule("{\"min\":5000,\"max\":10000,\"day_from\":15,\"day_to\":20}", 50L);
        assertThat(engine.categorize(tx("x", "7000.00", LocalDate.of(2026, 1, 17)), List.of(rule))).isEqualTo(50L);
        assertThat(engine.categorize(tx("x", "7000.00", LocalDate.of(2026, 1, 10)), List.of(rule))).isNull();
        assertThat(engine.categorize(tx("x", "12000.00", LocalDate.of(2026, 1, 17)), List.of(rule))).isNull();
    }

    @Test
    void firstMatchWinsInGivenOrder() {
        Rule first = rule("{\"operation_contains\":\"перевод\"}", 1L);
        Rule second = rule("{\"operation_contains\":\"перевод\"}", 2L);
        assertThat(engine.categorize(tx("Перевод средств"), List.of(first, second))).isEqualTo(1L);
    }

    @Test
    void noMatchReturnsNull() {
        assertThat(engine.categorize(tx("неизвестная операция"), List.of())).isNull();
    }

    @Test
    void emptyParamsNeverMatches() {
        Rule rule = rule("{}", 60L);
        assertThat(engine.categorize(tx("перевод"), List.of(rule))).isNull();
    }

    @Test
    void unknownConditionKeysNeverMatch() {
        // Regression: a legacy rule holding only the removed merchant_contains
        // key must not match every transaction (empty AND = vacuous true).
        Rule rule = rule("{\"merchant_contains\":\"заробот\"}", 80L);
        assertThat(engine.categorize(tx("Покупка KLGAPT 101"), List.of(rule))).isNull();
    }

    @Test
    void invalidRegexIsSkipped() {
        Rule rule = rule("{\"purpose_regex\":\"[\"}", 70L);
        assertThat(engine.categorize(transfer("оплата аренды"), List.of(rule))).isNull();
    }

    private Rule rule(String params, Long categoryId) {
        return new Rule(1L, null, params, categoryId, 0, false);
    }

    private Transaction tx(String text) {
        return tx(text, "10.00", LocalDate.of(2026, 1, 1));
    }

    private Transaction tx(String text, String amount) {
        return tx(text, amount, LocalDate.of(2026, 1, 1));
    }

    private Transaction tx(String text, String amount, LocalDate date) {
        return new Transaction(1L, 1L, 1, date, text, text, new BigDecimal(amount), "debit", "fp");
    }

    private Transaction transfer(String purpose) {
        return new Transaction(1L, 1L, 1, LocalDate.of(2026, 1, 1),
                "Перевод средств", purpose, new BigDecimal("10.00"), "credit", "fp");
    }
}
