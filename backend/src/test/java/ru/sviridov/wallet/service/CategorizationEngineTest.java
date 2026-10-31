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
        Rule rule = rule(Rule.TYPE_MERCHANT_CONTAINS, "{\"value\":\"MIR KORMA\"}", 10L);
        assertThat(engine.categorize(tx("ОПЛАТА MIR KORMA"), List.of(rule))).isEqualTo(10L);
    }

    @Test
    void merchantRegexMatchesCaseInsensitive() {
        Rule rule = rule(Rule.TYPE_MERCHANT_REGEX, "{\"pattern\":\"korma\"}", 20L);
        assertThat(engine.categorize(tx("MIR KORMA"), List.of(rule))).isEqualTo(20L);
    }

    @Test
    void amountRangeIsInclusive() {
        Rule rule = rule(Rule.TYPE_AMOUNT_RANGE, "{\"min\":100,\"max\":500}", 30L);
        assertThat(engine.categorize(tx("x", "500.00"), List.of(rule))).isEqualTo(30L);
        assertThat(engine.categorize(tx("x", "500.01"), List.of(rule))).isNull();
    }

    @Test
    void dayOfMonthMatches() {
        Rule rule = rule(Rule.TYPE_DAY_OF_MONTH, "{\"day\":15}", 40L);
        assertThat(engine.categorize(tx("x", "10.00", LocalDate.of(2026, 1, 15)), List.of(rule))).isEqualTo(40L);
        assertThat(engine.categorize(tx("x", "10.00", LocalDate.of(2026, 1, 16)), List.of(rule))).isNull();
    }

    @Test
    void firstMatchWinsInGivenOrder() {
        Rule first = rule(Rule.TYPE_MERCHANT_CONTAINS, "{\"value\":\"korma\"}", 1L);
        Rule second = rule(Rule.TYPE_MERCHANT_CONTAINS, "{\"value\":\"korma\"}", 2L);
        assertThat(engine.categorize(tx("korma"), List.of(first, second))).isEqualTo(1L);
    }

    @Test
    void noMatchReturnsNull() {
        assertThat(engine.categorize(tx("unknown merchant"), List.of())).isNull();
    }

    @Test
    void invalidRegexIsSkipped() {
        Rule rule = rule(Rule.TYPE_MERCHANT_REGEX, "{\"pattern\":\"[\"}", 50L);
        assertThat(engine.categorize(tx("korma"), List.of(rule))).isNull();
    }

    private Rule rule(String type, String params, Long categoryId) {
        return new Rule(1L, type, params, categoryId, 0, false);
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
