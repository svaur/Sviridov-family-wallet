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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class RuleService {

    /** User-authored rules override built-in defaults (which use priority 0). */
    private static final int DEFAULT_USER_PRIORITY = 100;

    private final RuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final DefaultDataSeeder defaultDataSeeder;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;

    public RuleService(RuleRepository ruleRepository, CategoryRepository categoryRepository,
                       DefaultDataSeeder defaultDataSeeder, TransactionService transactionService,
                       ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
        this.defaultDataSeeder = defaultDataSeeder;
        this.transactionService = transactionService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<RuleResponse> list(Long userId) {
        defaultDataSeeder.ensureDefaults(userId);
        return ruleRepository.findByUserIdOrderByPriorityDescIdAsc(userId).stream()
                .map(rule -> RuleResponse.from(rule, objectMapper))
                .toList();
    }

    @Transactional
    public RuleResponse create(Long userId, RuleRequest request) {
        defaultDataSeeder.ensureDefaults(userId);
        validateCategory(userId, request.categoryId());
        String params = normalizeParams(request);
        int priority = request.priority() == null ? DEFAULT_USER_PRIORITY : request.priority();
        Rule rule = ruleRepository.save(new Rule(userId, request.type(), params, request.categoryId(), priority, false));
        transactionService.reCategorizeAll(userId);
        return RuleResponse.from(rule, objectMapper);
    }

    @Transactional
    public RuleResponse update(Long userId, Long id, RuleRequest request) {
        Rule rule = ruleRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Правило не найдено"));
        validateCategory(userId, request.categoryId());
        String params = normalizeParams(request);
        rule.setType(request.type());
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

    /** Validates per-type params and returns the canonical JSON to persist. */
    private String normalizeParams(RuleRequest request) {
        Map<String, Object> params = request.params() == null ? Map.of() : request.params();
        return switch (request.type()) {
            case Rule.TYPE_MERCHANT_CONTAINS -> {
                String value = requiredString(params, Rule.KEY_VALUE, "merchant_contains");
                yield json(Map.of(Rule.KEY_VALUE, value));
            }
            case Rule.TYPE_MERCHANT_REGEX -> {
                String pattern = requiredString(params, Rule.KEY_PATTERN, "merchant_regex");
                try {
                    Pattern.compile(pattern);
                } catch (PatternSyntaxException e) {
                    throw new BadRequestException("Некорректное регулярное выражение: " + e.getMessage());
                }
                yield json(Map.of(Rule.KEY_PATTERN, pattern));
            }
            case Rule.TYPE_AMOUNT_RANGE -> {
                BigDecimal min = optionalDecimal(params, Rule.KEY_MIN);
                BigDecimal max = optionalDecimal(params, Rule.KEY_MAX);
                if (min == null && max == null) {
                    throw new BadRequestException("Для правила 'amount_range' укажите 'min' и/или 'max'");
                }
                Map<String, Object> range = new LinkedHashMap<>();
                if (min != null) {
                    range.put(Rule.KEY_MIN, min);
                }
                if (max != null) {
                    range.put(Rule.KEY_MAX, max);
                }
                yield json(range);
            }
            case Rule.TYPE_DAY_OF_MONTH -> {
                Integer day = optionalInteger(params, Rule.KEY_DAY);
                if (day == null || day < 1 || day > 31) {
                    throw new BadRequestException("Для правила 'day_of_month' укажите 'day' от 1 до 31");
                }
                yield json(Map.of(Rule.KEY_DAY, day));
            }
            default -> throw new BadRequestException("Неизвестный тип правила: " + request.type());
        };
    }

    private String requiredString(Map<String, Object> params, String key, String type) {
        Object value = params.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BadRequestException("Для правила '" + type + "' укажите '" + key + "'");
        }
        return String.valueOf(value).trim();
    }

    private BigDecimal optionalDecimal(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BadRequestException("Некорректное число в '" + key + "'");
        }
    }

    private Integer optionalInteger(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value)).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new BadRequestException("Некорректное целое число в '" + key + "'");
        }
    }

    private String json(Map<String, Object> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать параметры правила", e);
        }
    }
}
