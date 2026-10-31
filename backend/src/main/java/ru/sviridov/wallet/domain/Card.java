package ru.sviridov.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A bank card owned by a user. Each card is an independent stream of statement
 * imports. {@code last4} is used to reconcile the card against the card number
 * extracted from an imported PDF.
 */
@Entity
@Table(name = "cards")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(length = 120)
    private String bank;

    @Column(length = 4)
    private String last4;

    @Column(length = 3, nullable = false)
    private String currency;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Card() {
    }

    public Card(Long userId, String displayName, String bank, String last4, String currency) {
        this.userId = userId;
        this.displayName = displayName;
        this.bank = bank;
        this.last4 = last4;
        this.currency = (currency == null || currency.isBlank()) ? "RUB" : currency.trim().toUpperCase();
        this.archived = false;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getBank() {
        return bank;
    }

    public void setBank(String bank) {
        this.bank = bank;
    }

    public String getLast4() {
        return last4;
    }

    public void setLast4(String last4) {
        this.last4 = last4;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
