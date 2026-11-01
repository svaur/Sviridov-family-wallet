package ru.sviridov.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.CategoryRepository;
import ru.sviridov.wallet.domain.Rule;
import ru.sviridov.wallet.domain.RuleRepository;
import ru.sviridov.wallet.web.BadRequestException;
import ru.sviridov.wallet.web.NotFoundException;
import ru.sviridov.wallet.web.dto.RuleRequest;
import ru.sviridov.wallet.web.dto.RuleResponse;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Categorization rules CRUD. A rule is a category + a flat object of optional
 * conditions ({@code operation_contains}, {@code operation_regex},
 * {@code purpose_contains}, {@code purpose_regex}, {@code min}, {@code max},
 * {@code day_from}, {@code day_to}); the engine matches when ALL present
 * conditions match (implicit AND, see {@link CategorizationEngine}).
 *
 * <p>Responsibility split: this class validates and canonicalises input
 * (regexes are compiled once to reject bad patterns, {@code *_contains} values
 * are normalised so case does not matter later), while matching itself lives in
 * the engine. Every mutation triggers {@link TransactionService#reCategorizeAll}
 * so the feed reflects rule changes immediately.</p>
 */
@Service
public class RuleService {

    /** User-authored rules default to a priority above lower-priority rules. */
    private static final int DEFAULT_USER_PRIORITY = 100;

    private static final Set<String> KNOWN_KEYS = Set.of(
            Rule.KEY_OPERATION_CONTAINS, Rule.KEY_OPERATION_REGEX,
            Rule.KEY_PURPOSE_CONTAINS, Rule.KEY_PURPOSE_REGEX,
            Rule.KEY_MIN, Rule.KEY_MAX, Rule.KEY_DAY_FROM, Rule.KEY_DAY_TO);

    private final RuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;
    private final TextNormalizer textNormalizer;

    public RuleService(RuleRepository ruleRepository, CategoryRepository categoryRepository,
                       TransactionService transactionService,
                       ObjectMapper objectMapper, TextNormalizer textNormalizer) {
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
        this.transactionService = transactionService;
        this.objectMapper = objectMapper;
        this.textNormalizer = textNormalizer;
    }

    @Transactional(readOnly = true)
    public List<RuleResponse> list(Long userId) {
        return ruleRepository.findByUserIdOrderByPriorityDescIdAsc(userId).stream()
                .map(rule -> RuleResponse.from(rule, objectMapper))
                .toList();
    }

    @Transactional
    public RuleResponse create(Long userId, RuleRequest request) {
        validateCategory(userId, request.categoryId());
        String params = normalizeParams(request);
        int priority = request.priority() == null ? DEFAULT_USER_PRIORITY : request.priority();
        Rule rule = ruleRepository.save(new Rule(userId, normalizeName(request.name()), params,
                request.categoryId(), priority, false));
        transactionService.reCategorizeAll(userId);
        return RuleResponse.from(rule, objectMapper);
    }

    @Transactional
    public RuleResponse update(Long userId, Long id, RuleRequest request) {
        Rule rule = ruleRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Правило не найдено"));
        validateCategory(userId, request.categoryId());
        String params = normalizeParams(request);
        rule.setName(normalizeName(request.name()));
        rule.setParams(params);
        rule.setCategoryId(request.categoryId());
        rule.setPriority(request.priority() == null ? DEFAULT_USER_PRIORITY : request.priority());
        ruleRepository.save(rule);
        transactionService.reCategorizeAll(userId);
        return RuleResponse.from(rule, objectMapper);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Rule rule = ruleRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Правило не найдено"));
        ruleRepository.delete(rule);
        ruleRepository.flush();
        transactionService.reCategorizeAll(userId);
    }

    private void validateCategory(Long userId, Long categoryId) {
        if (categoryId == null || categoryRepository.findByIdAndUserId(categoryId, userId).isEmpty()) {
            throw new NotFoundException("Категория не найдена");
        }
    }

    /** Validates the flat condition object and returns the canonical JSON to persist. */
    private String normalizeParams(RuleRequest request) {
        Map<String, Object> in = request.params() == null ? Map.of() : request.params();
        for (String key : in.keySet()) {
            if (!KNOWN_KEYS.contains(key)) {
                throw new BadRequestException("Неизвестное условие правила: " + key);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();

        if (in.containsKey(Rule.KEY_OPERATION_CONTAINS)) {
            String value = optionalString(in, Rule.KEY_OPERATION_CONTAINS);
            if (value == null) {
                throw new BadRequestException("Условие 'operation_contains' не может быть пустым");
            }
            out.put(Rule.KEY_OPERATION_CONTAINS, textNormalizer.normalize(value));
        }

        if (in.containsKey(Rule.KEY_OPERATION_REGEX)) {
            String pattern = optionalString(in, Rule.KEY_OPERATION_REGEX);
            if (pattern == null) {
                throw new BadRequestException("Условие 'operation_regex' не может быть пустым");
            }
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                throw new BadRequestException("Некорректное регулярное выражение: " + e.getMessage());
            }
            out.put(Rule.KEY_OPERATION_REGEX, pattern);
        }

        if (in.containsKey(Rule.KEY_PURPOSE_CONTAINS)) {
            String value = optionalString(in, Rule.KEY_PURPOSE_CONTAINS);
            if (value == null) {
                throw new BadRequestException("Условие 'purpose_contains' не может быть пустым");
            }
            out.put(Rule.KEY_PURPOSE_CONTAINS, textNormalizer.normalize(value));
        }

        if (in.containsKey(Rule.KEY_PURPOSE_REGEX)) {
            String pattern = optionalString(in, Rule.KEY_PURPOSE_REGEX);
            if (pattern == null) {
                throw new BadRequestException("Условие 'purpose_regex' не может быть пустым");
            }
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                throw new BadRequestException("Некорректное регулярное выражение: " + e.getMessage());
            }
            out.put(Rule.KEY_PURPOSE_REGEX, pattern);
        }

        BigDecimal min = null;
        BigDecimal max = null;
        if (in.containsKey(Rule.KEY_MIN)) {
            min = requiredDecimal(in, Rule.KEY_MIN);
            if (min.signum() < 0) {
                throw new BadRequestException("'min' не может быть отрицательным");
            }
            out.put(Rule.KEY_MIN, min);
        }
        if (in.containsKey(Rule.KEY_MAX)) {
            max = requiredDecimal(in, Rule.KEY_MAX);
            if (max.signum() < 0) {
                throw new BadRequestException("'max' не может быть отрицательным");
            }
            out.put(Rule.KEY_MAX, max);
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new BadRequestException("'min' не может быть больше 'max'");
        }

        Integer dayFrom = null;
        Integer dayTo = null;
        if (in.containsKey(Rule.KEY_DAY_FROM)) {
            dayFrom = requiredDay(in, Rule.KEY_DAY_FROM);
            out.put(Rule.KEY_DAY_FROM, dayFrom);
        }
        if (in.containsKey(Rule.KEY_DAY_TO)) {
            dayTo = requiredDay(in, Rule.KEY_DAY_TO);
            out.put(Rule.KEY_DAY_TO, dayTo);
        }
        if (dayFrom != null && dayTo != null && dayFrom > dayTo) {
            throw new BadRequestException("'day_from' не может быть больше 'day_to'");
        }

        if (out.isEmpty()) {
            throw new BadRequestException("Задайте хотя бы одно условие правила");
        }

        return json(out);
    }

    private String optionalString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private BigDecimal requiredDecimal(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            throw new BadRequestException("Укажите значение '" + key + "'");
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("Некорректное число в '" + key + "'");
        }
    }

    private Integer requiredDay(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            throw new BadRequestException("Укажите значение '" + key + "'");
        }
        try {
            int day = new BigDecimal(String.valueOf(value).trim()).intValueExact();
            if (day < 1 || day > 31) {
                throw new BadRequestException("'" + key + "' должен быть от 1 до 31");
            }
            return day;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new BadRequestException("Некорректное целое число в '" + key + "'");
        }
    }

    /** Trims the name; blank becomes null so the UI can fall back to the params label. */
    private String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String json(Map<String, Object> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать параметры правила", e);
        }
    }
}
