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
    private final TextNormalizer textNormalizer;

    public CategorizationEngine(ObjectMapper objectMapper, TextNormalizer textNormalizer) {
        this.objectMapper = objectMapper;
        this.textNormalizer = textNormalizer;
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
        if (params.isEmpty() || !hasKnownCondition(params)) {
            return false;
        }

        String operation = tx.getOperation();

        if (params.containsKey(Rule.KEY_OPERATION_CONTAINS)) {
            String needle = textNormalizer.normalize(asString(params.get(Rule.KEY_OPERATION_CONTAINS)));
            if (operation == null || needle == null
                    || !textNormalizer.normalize(operation).contains(needle)) {
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

        String purpose = tx.getPurpose();

        if (params.containsKey(Rule.KEY_PURPOSE_CONTAINS)) {
            String needle = textNormalizer.normalize(asString(params.get(Rule.KEY_PURPOSE_CONTAINS)));
            if (purpose == null || needle == null
                    || !textNormalizer.normalize(purpose).contains(needle)) {
                return false;
            }
        }

        if (params.containsKey(Rule.KEY_PURPOSE_REGEX)) {
            String pattern = asString(params.get(Rule.KEY_PURPOSE_REGEX));
            if (purpose == null || pattern == null || pattern.isBlank()) {
                return false;
            }
            try {
                if (!Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                        .matcher(purpose)
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

    /**
     * A rule must contain at least one recognised condition key. Rules whose
     * params hold only unknown keys (e.g. legacy {@code merchant_contains})
     * must never match — otherwise the empty AND over zero conditions would be
     * vacuously true and the rule would match every transaction.
     */
    private static boolean hasKnownCondition(Map<String, Object> params) {
        return params.containsKey(Rule.KEY_OPERATION_CONTAINS)
                || params.containsKey(Rule.KEY_OPERATION_REGEX)
                || params.containsKey(Rule.KEY_PURPOSE_CONTAINS)
                || params.containsKey(Rule.KEY_PURPOSE_REGEX)
                || params.containsKey(Rule.KEY_MIN)
                || params.containsKey(Rule.KEY_MAX)
                || params.containsKey(Rule.KEY_DAY_FROM)
                || params.containsKey(Rule.KEY_DAY_TO);
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
