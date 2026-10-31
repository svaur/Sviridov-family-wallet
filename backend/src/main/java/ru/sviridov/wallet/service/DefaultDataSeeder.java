package ru.sviridov.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.Category;
import ru.sviridov.wallet.domain.CategoryRepository;
import ru.sviridov.wallet.domain.Rule;
import ru.sviridov.wallet.domain.RuleRepository;

import java.util.Map;

/**
 * Seeds a small set of default categories and merchant rules for a user. Runs
 * lazily and idempotently — the first time a user touches categories/rules.
 */
@Component
public class DefaultDataSeeder {

    private final CategoryRepository categoryRepository;
    private final RuleRepository ruleRepository;
    private final ObjectMapper objectMapper;

    public DefaultDataSeeder(CategoryRepository categoryRepository, RuleRepository ruleRepository,
                             ObjectMapper objectMapper) {
        this.categoryRepository = categoryRepository;
        this.ruleRepository = ruleRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Runs in a NEW writable transaction ({@code REQUIRES_NEW}) so it can insert
     * defaults even when invoked from a read-only method such as {@code list()}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureDefaults(Long userId) {
        if (categoryRepository.countByUserId(userId) > 0) {
            return;
        }
        Category products = categoryRepository.save(new Category(userId, "Продукты"));
        Category transport = categoryRepository.save(new Category(userId, "Транспорт"));
        Category housing = categoryRepository.save(new Category(userId, "ЖКХ"));
        Category pharmacy = categoryRepository.save(new Category(userId, "Аптека"));

        addContainsRules(userId, products.getId(),
                "магнит", "пятёрочк", "пятерочк", "перекрест", "дикси", "лента",
                "продукт", "супермаркет", "вкусвилл", "ашан", "окей");
        addContainsRules(userId, transport.getId(),
                "метро", "такси", "яндекс", "автобус", "троллейбус", "трамвай",
                "азс", "заправк", "бензин", "аэроэкспресс");
        addContainsRules(userId, housing.getId(),
                "жкх", "тсж", "управляющ", "водоканал", "энергосбыт", "теплосет",
                "газпром", "интернет", "связь", "мособлеирц");
        addContainsRules(userId, pharmacy.getId(),
                "аптек", "фарм", "здоровье", "еаптека", "горздрав");
    }

    private void addContainsRules(Long userId, Long categoryId, String... keywords) {
        for (String keyword : keywords) {
            ruleRepository.save(new Rule(userId, Rule.TYPE_MERCHANT_CONTAINS,
                    json(Map.of(Rule.KEY_VALUE, keyword)), categoryId, 0, false));
        }
    }

    private String json(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize rule params", e);
        }
    }
}
