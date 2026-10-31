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
 * A detected break between consecutive statements of the same card. The gap
 * is the date range (inclusive) that no statement covers, e.g. a statement
 * ending 30.09 and the next starting 15.10 produces gap 01.10..14.10.
 */
@Entity
@Table(name = "statement_gaps")
public class StatementGap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(name = "gap_from", nullable = false)
    private LocalDate gapFrom;

    @Column(name = "gap_to", nullable = false)
    private LocalDate gapTo;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    protected StatementGap() {
    }

    public StatementGap(Long cardId, LocalDate gapFrom, LocalDate gapTo) {
        this.cardId = cardId;
        this.gapFrom = gapFrom;
        this.gapTo = gapTo;
        this.detectedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getCardId() {
        return cardId;
    }

    public LocalDate getGapFrom() {
        return gapFrom;
    }

    public LocalDate getGapTo() {
        return gapTo;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }
}
