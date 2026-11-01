package ru.sviridov.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A single operation row. {@code amount} holds the value of the filled column
 * (Расход→debit, Приход→credit); the opposite column is always null.
 * {@code source} records the funding source: a bank statement import
 * ({@link #SOURCE_STATEMENT}), a screenshot (bank-feed) import
 * ({@link #SOURCE_SCREENSHOT}) or a manual entry tied to a card, cash or other
 * ({@link #SOURCE_CARD}, {@link #SOURCE_CASH}, {@link #SOURCE_OTHER}); the card
 * is optional (null for cash/other). {@code fingerprint} is the SHA-256 dedup
 * key for statement rows; rows are soft-deleted via {@code deletedAt} (tombstone).
 * {@code categoryId} is filled by the categorizer. {@code categoryManual} marks
 * a category assigned by hand, which rules must never overwrite.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    public static final String SOURCE_STATEMENT = "statement";
    public static final String SOURCE_SCREENSHOT = "screenshot";
    public static final String SOURCE_CARD = "card";
    public static final String SOURCE_CASH = "cash";
    public static final String SOURCE_OTHER = "other";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "statement_id")
    private Long statementId;

    @Column(name = "row_number")
    private Integer rowNumber;

    @Column(nullable = false)
    private LocalDate date;

    @Column(length = 200)
    private String operation;

    @Column(columnDefinition = "text")
    private String purpose;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String type;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "category_manual", nullable = false)
    private boolean categoryManual;

    @Column(name = "is_transfer", nullable = false)
    private boolean isTransfer;

    @Column(name = "is_excluded", nullable = false)
    private boolean isExcluded;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(length = 64)
    private String fingerprint;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Transaction() {
    }

    public Transaction(Long userId, Long cardId, Integer rowNumber, LocalDate date, String operation,
                       String purpose, BigDecimal amount, String type, String fingerprint) {
        this.userId = userId;
        this.cardId = cardId;
        this.rowNumber = rowNumber;
        this.date = date;
        this.operation = operation;
        this.purpose = purpose;
        this.amount = amount;
        this.type = type;
        this.source = SOURCE_STATEMENT;
        this.fingerprint = fingerprint;
        this.isTransfer = false;
        this.isExcluded = false;
        this.categoryManual = false;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCardId() {
        return cardId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public Long getStatementId() {
        return statementId;
    }

    public void setStatementId(Long statementId) {
        this.statementId = statementId;
    }

    public Integer getRowNumber() {
        return rowNumber;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getPurpose() {
        return purpose;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getType() {
        return type;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public boolean isCategoryManual() {
        return categoryManual;
    }

    public void setCategoryManual(boolean categoryManual) {
        this.categoryManual = categoryManual;
    }

    public boolean isTransfer() {
        return isTransfer;
    }

    public boolean isExcluded() {
        return isExcluded;
    }

    public void setIsTransfer(boolean isTransfer) {
        this.isTransfer = isTransfer;
    }

    public void setIsExcluded(boolean isExcluded) {
        this.isExcluded = isExcluded;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
