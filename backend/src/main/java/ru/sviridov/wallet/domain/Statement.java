package ru.sviridov.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Import fact for a statement. Only metadata is persisted — the PDF itself is
 * never stored. Shown to the user later in the upload history.
 */
@Entity
@Table(name = "statements")
public class Statement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "period_from")
    private LocalDate periodFrom;

    @Column(name = "period_to")
    private LocalDate periodTo;

    @Column(name = "parser_version", length = 40)
    private String parserVersion;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "txn_count")
    private Integer txnCount;

    @Column(name = "row_count_raw")
    private Integer rowCountRaw;

    @Column(name = "card_number_extracted", length = 20)
    private String cardNumberExtracted;

    @Column(length = 120)
    private String contract;

    @Column(length = 40)
    private String account;

    protected Statement() {
    }

    public Statement(Long cardId, LocalDate periodFrom, LocalDate periodTo, String parserVersion,
                     String status, Integer txnCount, Integer rowCountRaw, String cardNumberExtracted,
                     String contract, String account) {
        this.cardId = cardId;
        this.uploadedAt = Instant.now();
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.parserVersion = parserVersion;
        this.status = status;
        this.txnCount = txnCount;
        this.rowCountRaw = rowCountRaw;
        this.cardNumberExtracted = cardNumberExtracted;
        this.contract = contract;
        this.account = account;
    }

    public Long getId() {
        return id;
    }

    public Long getCardId() {
        return cardId;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public LocalDate getPeriodFrom() {
        return periodFrom;
    }

    public LocalDate getPeriodTo() {
        return periodTo;
    }

    public String getParserVersion() {
        return parserVersion;
    }

    public String getStatus() {
        return status;
    }

    public Integer getTxnCount() {
        return txnCount;
    }

    public Integer getRowCountRaw() {
        return rowCountRaw;
    }

    public String getCardNumberExtracted() {
        return cardNumberExtracted;
    }

    public String getContract() {
        return contract;
    }

    public String getAccount() {
        return account;
    }
}
