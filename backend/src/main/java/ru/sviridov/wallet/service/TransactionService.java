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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Transaction feed operations: listing, manual entry, soft delete, manual
 * category assignment, comments and bulk re-categorization.
 *
 * <p>Two invariants live here and are easy to break:</p>
 * <ul>
 *   <li>Deleting from the feed is a SOFT delete (tombstone) — see
 *       {@link #delete}. The fingerprint stays in the DB, so re-importing the
 *       same statement does not resurrect a transaction the user removed.</li>
 *   <li>A manually assigned category sets {@code category_manual = true}, and
 *       {@link #reCategorizeAll} skips those rows. Auto-apply must never
 *       overwrite an explicit human choice (see AGENTS.md → Categorization).</li>
 * </ul>
 */
@Service
public class TransactionService {

    /**
     * Manual entries have no bank "operation" text; we write a constant instead
     * of leaving the column empty so the feed is not visually ambiguous, and so
     * the value is recognisable in the UI.
     */
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
     * Edits the user-visible fields of a transaction: operation, amount, date
     * (the same three the import preview lets you correct).
     *
     * <p>The fingerprint is deliberately left untouched. It is an <em>import
     * identity</em> — "this row of that statement was already taken" — not a
     * content hash, so a hand correction must not reopen the door to importing
     * the same statement row twice.</p>
     */
    @Transactional
    public TransactionResponse update(Long userId, Long id, String operation, BigDecimal amount, LocalDate date) {
        Transaction tx = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Операция не найдена"));
        if (tx.getDeletedAt() != null) {
            throw new NotFoundException("Операция не найдена");
        }
        tx.setOperation(operation == null || operation.isBlank() ? null : operation.trim());
        tx.setAmount(amount);
        tx.setDate(date);
        tx = transactionRepository.save(tx);

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
