package ru.sviridov.wallet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.CardRepository;
import ru.sviridov.wallet.domain.Category;
import ru.sviridov.wallet.domain.CategoryRepository;
import ru.sviridov.wallet.domain.Rule;
import ru.sviridov.wallet.domain.RuleRepository;
import ru.sviridov.wallet.domain.Transaction;
import ru.sviridov.wallet.domain.TransactionRepository;
import ru.sviridov.wallet.web.BadRequestException;
import ru.sviridov.wallet.web.NotFoundException;
import ru.sviridov.wallet.web.dto.TransactionRequest;
import ru.sviridov.wallet.web.dto.TransactionResponse;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private static final String MANUAL_OPERATION = "добавлено вручную";

    private final TransactionRepository transactionRepository;
    private final RuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final CategorizationEngine categorizationEngine;
    private final CardRepository cardRepository;
    private final MerchantNormalizer merchantNormalizer;
    private final DefaultDataSeeder defaultDataSeeder;

    public TransactionService(TransactionRepository transactionRepository, RuleRepository ruleRepository,
                              CategoryRepository categoryRepository, CategorizationEngine categorizationEngine,
                              CardRepository cardRepository, MerchantNormalizer merchantNormalizer,
                              DefaultDataSeeder defaultDataSeeder) {
        this.transactionRepository = transactionRepository;
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
        this.categorizationEngine = categorizationEngine;
        this.cardRepository = cardRepository;
        this.merchantNormalizer = merchantNormalizer;
        this.defaultDataSeeder = defaultDataSeeder;
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
     * Creates a manual transaction. The funding source is card / cash / other;
     * a card must be chosen (and owned by the user) only when source is "card".
     * When no category is given, the rule set is applied automatically.
     */
    @Transactional
    public TransactionResponse create(Long userId, TransactionRequest request) {
        Long cardId = request.cardId();
        if (Transaction.SOURCE_CARD.equals(request.source())) {
            if (cardId == null) {
                throw new BadRequestException("Для источника «карта» необходимо выбрать карту");
            }
            cardRepository.findByIdAndUserId(cardId, userId)
                    .orElseThrow(() -> new NotFoundException("Карта не найдена"));
        } else {
            cardId = null;
        }

        if (request.categoryId() != null) {
            categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                    .orElseThrow(() -> new NotFoundException("Категория не найдена"));
        }

        defaultDataSeeder.ensureDefaults(userId);
        List<Rule> rules = ruleRepository.findByUserIdOrderByPriorityDescIdAsc(userId);

        Transaction tx = new Transaction(userId, cardId, null, request.date(),
                MANUAL_OPERATION, request.purpose(), request.amount(), request.type(), null);
        tx.setSource(request.source());
        tx.setMerchantNormalized(merchantNormalizer.normalize(request.purpose()));
        tx.setCategoryId(request.categoryId() != null
                ? request.categoryId()
                : categorizationEngine.categorize(tx, rules));
        tx = transactionRepository.save(tx);

        String categoryName = tx.getCategoryId() == null
                ? null
                : categoryRepository.findById(tx.getCategoryId()).map(Category::getName).orElse(null);
        return TransactionResponse.from(tx, categoryName);
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
