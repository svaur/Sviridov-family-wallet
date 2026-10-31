package ru.sviridov.wallet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.Category;
import ru.sviridov.wallet.domain.CategoryRepository;
import ru.sviridov.wallet.domain.Rule;
import ru.sviridov.wallet.domain.RuleRepository;
import ru.sviridov.wallet.domain.Transaction;
import ru.sviridov.wallet.domain.TransactionRepository;
import ru.sviridov.wallet.web.dto.TransactionResponse;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final RuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final CategorizationEngine categorizationEngine;

    public TransactionService(TransactionRepository transactionRepository, RuleRepository ruleRepository,
                              CategoryRepository categoryRepository, CategorizationEngine categorizationEngine) {
        this.transactionRepository = transactionRepository;
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
        this.categorizationEngine = categorizationEngine;
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> list(Long userId) {
        Map<Long, String> categoryNames = categoryRepository.findByUserIdOrderByNameAsc(userId).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        return transactionRepository.findActiveByUserId(userId).stream()
                .map(t -> TransactionResponse.from(t, categoryNames.get(t.getCategoryId())))
                .toList();
    }

    /**
     * Re-runs the full rule set (priority order) over the user's active
     * transactions and updates category assignments in place. Called after any
     * rule or category change. Entities are managed, so Hibernate dirty checking
     * persists the changes on flush.
     */
    @Transactional
    public void reCategorizeAll(Long userId) {
        List<Rule> rules = ruleRepository.findByUserIdOrderByPriorityDescIdAsc(userId);
        for (Transaction tx : transactionRepository.findActiveByUserId(userId)) {
            Long categoryId = categorizationEngine.categorize(tx, rules);
            if (!Objects.equals(tx.getCategoryId(), categoryId)) {
                tx.setCategoryId(categoryId);
            }
        }
    }
}
