package ru.sviridov.wallet.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import ru.sviridov.wallet.domain.Rule;
import ru.sviridov.wallet.domain.Transaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Applies a user's rules to a transaction. Rules are evaluated in the order
 * given (by convention: priority descending, then id ascending) and the first
 * match wins. Returns the matched category id, or {@code null} when no rule
 * matched (the transaction stays UNCATEGORIZED).
 *
 * <p>A rule's {@code params} is a flat object of optional conditions; the rule
 * matches iff every present condition matches.</p>
 */
@Service
public class CategorizationEngine {

    private final ObjectMapper objectMapper;
    private final MerchantNormalizer merchantNormalizer;

    public CategorizationEngine(ObjectMapper objectMapper, MerchantNormalizer merchantNormalizer) {
        this.objectMapper = objectMapper;
        this.merchantNormalizer = merchantNormalizer;
    }

    public Long categorize(Transaction tx, List<Rule> rules) {
        for (Rule rule : rules) {
            if (matches(rule, tx)) {
                return rule.getCategoryId();
            }
        }
        return null;
    }

    private boolean matches(Rule rule, Transaction tx) {
        Map<String, Object> params = parseParams(rule.getParams());
        if (params.isEmpty()) {
            return false;
        }

        String merchant = tx.getMerchantNormalized();

        if (params.containsKey(Rule.KEY_MERCHANT_CONTAINS)) {
            String needle = merchantNormalizer.normalizeCore(asString(params.get(Rule.KEY_MERCHANT_CONTAINS)));
            if (merchant == null || needle == null || !merchant.contains(needle)) {
                return false;
            }
        }

        if (params.containsKey(Rule.KEY_MERCHANT_REGEX)) {
            String pattern = asString(params.get(Rule.KEY_MERCHANT_REGEX));
            if (merchant == null || pattern == null || pattern.isBlank()) {
                return false;
            }
            try {
                if (!Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                        .matcher(merchant)
                        .find()) {
                    return false;
                }
            } catch (PatternSyntaxException e) {
                return false;
            }
        }

        String operation = tx.getOperation();

        if (params.containsKey(Rule.KEY_OPERATION_CONTAINS)) {
            String needle = merchantNormalizer.normalizeCore(asString(params.get(Rule.KEY_OPERATION_CONTAINS)));
            if (operation == null || needle == null
                    || !merchantNormalizer.normalizeCore(operation).contains(needle)) {
                return false;
            }
        }

        if (params.containsKey(Rule.KEY_OPERATION_REGEX)) {
            String pattern = asString(params.get(Rule.KEY_OPERATION_REGEX));
            if (operation == null || pattern == null || pattern.isBlank()) {
                return false;
            }
            try {
                if (!Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                        .matcher(operation)
                        .find()) {
                    return false;
                }
            } catch (PatternSyntaxException e) {
                return false;
            }
        }

        BigDecimal amount = tx.getAmount();
        if (params.containsKey(Rule.KEY_MIN)) {
            BigDecimal min = asDecimal(params.get(Rule.KEY_MIN));
            if (amount == null || min == null || amount.compareTo(min) < 0) {
                return false;
            }
        }
        if (params.containsKey(Rule.KEY_MAX)) {
            BigDecimal max = asDecimal(params.get(Rule.KEY_MAX));
            if (amount == null || max == null || amount.compareTo(max) > 0) {
                return false;
            }
        }

        Integer day = tx.getDate() == null ? null : tx.getDate().getDayOfMonth();
        if (params.containsKey(Rule.KEY_DAY_FROM)) {
            Integer from = asInteger(params.get(Rule.KEY_DAY_FROM));
            if (day == null || from == null || day < from) {
                return false;
            }
        }
        if (params.containsKey(Rule.KEY_DAY_TO)) {
            Integer to = asInteger(params.get(Rule.KEY_DAY_TO));
            if (day == null || to == null || day > to) {
                return false;
            }
        }

        return true;
    }

    private Map<String, Object> parseParams(String params) {
        if (params == null || params.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(params, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static BigDecimal asDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
