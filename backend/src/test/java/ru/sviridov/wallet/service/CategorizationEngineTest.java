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
    private final MerchantNormalizer normalizer = new MerchantNormalizer();
    private final CategorizationEngine engine = new CategorizationEngine(objectMapper, normalizer);

    @Test
    void merchantContainsMatchesNormalizedMerchant() {
        Rule rule = rule("{\"merchant_contains\":\"MIR KORMA\"}", 10L);
        assertThat(engine.categorize(tx("ОПЛАТА MIR KORMA"), List.of(rule))).isEqualTo(10L);
    }

    @Test
    void merchantContainsIsCaseInsensitive() {
        Rule rule = rule("{\"merchant_contains\":\"KORMA\"}", 11L);
        assertThat(engine.categorize(tx("mir korma"), List.of(rule))).isEqualTo(11L);
    }

    @Test
    void merchantRegexMatchesCaseInsensitive() {
        Rule rule = rule("{\"merchant_regex\":\"KORMA\"}", 20L);
        assertThat(engine.categorize(tx("MIR KORMA"), List.of(rule))).isEqualTo(20L);
    }

    @Test
    void operationContainsMatches() {
        Rule rule = rule("{\"operation_contains\":\"перевод\"}", 13L);
        assertThat(engine.categorize(tx("Перевод средств"), List.of(rule))).isEqualTo(13L);
    }

    @Test
    void operationRegexMatchesCaseInsensitive() {
        Rule rule = rule("{\"operation_regex\":\"ПЕРЕВОД\"}", 14L);
        assertThat(engine.categorize(tx("Перевод средств"), List.of(rule))).isEqualTo(14L);
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
        Rule first = rule("{\"merchant_contains\":\"korma\"}", 1L);
        Rule second = rule("{\"merchant_contains\":\"korma\"}", 2L);
        assertThat(engine.categorize(tx("korma"), List.of(first, second))).isEqualTo(1L);
    }

    @Test
    void noMatchReturnsNull() {
        assertThat(engine.categorize(tx("unknown merchant"), List.of())).isNull();
    }

    @Test
    void emptyParamsNeverMatches() {
        Rule rule = rule("{}", 60L);
        assertThat(engine.categorize(tx("korma"), List.of(rule))).isNull();
    }

    @Test
    void invalidRegexIsSkipped() {
        Rule rule = rule("{\"merchant_regex\":\"[\"}", 70L);
        assertThat(engine.categorize(tx("korma"), List.of(rule))).isNull();
    }

    private Rule rule(String params, Long categoryId) {
        return new Rule(1L, null, params, categoryId, 0, false);
    }

    private Transaction tx(String merchant) {
        return tx(merchant, "10.00", LocalDate.of(2026, 1, 1));
    }

    private Transaction tx(String merchant, String amount) {
        return tx(merchant, amount, LocalDate.of(2026, 1, 1));
    }

    private Transaction tx(String merchant, String amount, LocalDate date) {
        Transaction t = new Transaction(1L, 1L, 1, date, merchant, merchant, new BigDecimal(amount), "debit", "fp");
        t.setMerchantNormalized(normalizer.normalize(merchant));
        return t;
    }
}
