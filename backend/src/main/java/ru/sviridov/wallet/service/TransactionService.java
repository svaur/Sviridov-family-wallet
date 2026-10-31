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

import java.time.Instant;
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

    public TransactionService(TransactionRepository transactionRepository, RuleRepository ruleRepository,
                              CategoryRepository categoryRepository, CategorizationEngine categorizationEngine,
                              CardRepository cardRepository) {
        this.transactionRepository = transactionRepository;
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
        this.categorizationEngine = categorizationEngine;
        this.cardRepository = cardRepository;
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

        List<Rule> rules = ruleRepository.findByUserIdOrderByPriorityDescIdAsc(userId);

        Transaction tx = new Transaction(userId, cardId, null, request.date(),
                MANUAL_OPERATION, request.purpose(), request.amount(), request.type(), null);
        tx.setSource(request.source());
        tx.setCategoryId(request.categoryId() != null
                ? request.categoryId()
                : categorizationEngine.categorize(tx, rules));
        tx.setCategoryManual(request.categoryId() != null);
        tx = transactionRepository.save(tx);

        String categoryName = tx.getCategoryId() == null
                ? null
                : categoryRepository.findById(tx.getCategoryId()).map(Category::getName).orElse(null);
        return TransactionResponse.from(tx, categoryName);
    }

    /** Soft-deletes a transaction (tombstone; its fingerprint still blocks re-import). */
    @Transactional
    public void delete(Long userId, Long id) {
        Transaction tx = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Операция не найдена"));
        tx.setDeletedAt(Instant.now());
        transactionRepository.save(tx);
    }

    /** Sets (or clears, on blank) a free-text comment on a transaction. */
    @Transactional
    public TransactionResponse updateComment(Long userId, Long id, String comment) {
        Transaction tx = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Операция не найдена"));
        if (tx.getDeletedAt() != null) {
            throw new NotFoundException("Операция не найдена");
        }
        tx.setComment(comment == null || comment.isBlank() ? null : comment.trim());
        tx = transactionRepository.save(tx);
        String categoryName = tx.getCategoryId() == null
                ? null
                : categoryRepository.findById(tx.getCategoryId()).map(Category::getName).orElse(null);
        return TransactionResponse.from(tx, categoryName);
    }

    /**
     * Assigns a category to a transaction manually (bypassing rules). The
     * assignment is marked manual so re-running rules never overwrites it.
     */
    @Transactional
    public TransactionResponse assignCategory(Long userId, Long id, Long categoryId) {
        Transaction tx = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Операция не найдена"));
        if (tx.getDeletedAt() != null) {
            throw new NotFoundException("Операция не найдена");
        }
        categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new NotFoundException("Категория не найдена"));
        tx.setCategoryId(categoryId);
        tx.setCategoryManual(true);
        tx = transactionRepository.save(tx);

        String categoryName = categoryRepository.findById(categoryId).map(Category::getName).orElse(null);
        return TransactionResponse.from(tx, categoryName);
    }

    /**
     * Marks a transaction as an internal transfer and, heuristically, also
     * marks any opposite-side transaction with the same amount and date (the
     * matching debit/credit pair), so analytics excludes both.
     */
    @Transactional
    public TransactionResponse markTransfer(Long userId, Long id) {
        Transaction tx = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Операция не найдена"));
        if (tx.getDeletedAt() != null) {
            throw new NotFoundException("Операция не найдена");
        }
        tx.setIsTransfer(true);
        tx = transactionRepository.save(tx);

        for (Transaction other : transactionRepository.findActiveByUserId(userId)) {
            if (other == tx || (other.getId() != null && other.getId().equals(tx.getId()))) {
                continue;
            }
            if (other.isTransfer()) {
                continue;
            }
            if (!other.getDate().equals(tx.getDate())) {
                continue;
            }
            if (other.getAmount().compareTo(tx.getAmount()) != 0) {
                continue;
            }
            if (other.getType().equals(tx.getType())) {
                continue;
            }
            other.setIsTransfer(true);
            transactionRepository.save(other);
        }

        String categoryName = tx.getCategoryId() == null
                ? null
                : categoryRepository.findById(tx.getCategoryId()).map(Category::getName).orElse(null);
        return TransactionResponse.from(tx, categoryName);
    }

    /**
     * Re-runs the full rule set (priority order) over the user's active
     * transactions and updates category assignments in place. Called after any
     * rule or category change. Transactions with a manually assigned category
     * are skipped. Entities are managed, so Hibernate dirty checking persists
     * the changes on flush.
     *
     * @return the number of transactions whose category changed
     */
    @Transactional
    public int reCategorizeAll(Long userId) {
        List<Rule> rules = ruleRepository.findByUserIdOrderByPriorityDescIdAsc(userId);
        int changed = 0;
        for (Transaction tx : transactionRepository.findActiveByUserId(userId)) {
            if (tx.isCategoryManual()) {
                continue;
            }
            Long categoryId = categorizationEngine.categorize(tx, rules);
            if (!Objects.equals(tx.getCategoryId(), categoryId)) {
                tx.setCategoryId(categoryId);
                changed++;
            }
        }
        return changed;
    }
}
