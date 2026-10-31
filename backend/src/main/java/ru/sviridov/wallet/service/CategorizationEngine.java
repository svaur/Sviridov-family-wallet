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
        return switch (rule.getType()) {
            case Rule.TYPE_MERCHANT_CONTAINS -> merchantContains(params, tx);
            case Rule.TYPE_MERCHANT_REGEX -> merchantRegex(params, tx);
            case Rule.TYPE_AMOUNT_RANGE -> amountRange(params, tx);
            case Rule.TYPE_DAY_OF_MONTH -> dayOfMonth(params, tx);
            default -> false;
        };
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

    private boolean merchantContains(Map<String, Object> params, Transaction tx) {
        if (tx.getMerchantNormalized() == null) {
            return false;
        }
        String needle = merchantNormalizer.normalizeCore(asString(params.get(Rule.KEY_VALUE)));
        return needle != null && tx.getMerchantNormalized().contains(needle);
    }

    private boolean merchantRegex(Map<String, Object> params, Transaction tx) {
        String pattern = asString(params.get(Rule.KEY_PATTERN));
        if (pattern == null || pattern.isBlank() || tx.getMerchantNormalized() == null) {
            return false;
        }
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(tx.getMerchantNormalized())
                    .find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private boolean amountRange(Map<String, Object> params, Transaction tx) {
        BigDecimal min = asDecimal(params.get(Rule.KEY_MIN));
        BigDecimal max = asDecimal(params.get(Rule.KEY_MAX));
        if (min == null && max == null) {
            return false;
        }
        if (tx.getAmount() == null) {
            return false;
        }
        if (min != null && tx.getAmount().compareTo(min) < 0) {
            return false;
        }
        return max == null || tx.getAmount().compareTo(max) <= 0;
    }

    private boolean dayOfMonth(Map<String, Object> params, Transaction tx) {
        Integer day = asInteger(params.get(Rule.KEY_DAY));
        return day != null && tx.getDate() != null && tx.getDate().getDayOfMonth() == day;
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
