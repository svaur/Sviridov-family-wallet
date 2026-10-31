package ru.sviridov.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A bank card owned by a user. Cards are created automatically when a statement
 * is imported: the full masked {@code cardNumber} extracted from the PDF is the
 * natural identity, and {@code last4} is derived from it for display. Users can
 * rename the card's display name but do not manage cards manually.
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

    @Column(name = "card_number", length = 20)
    private String cardNumber;

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

    public Card(Long userId, String cardNumber, String displayName) {
        this.userId = userId;
        this.cardNumber = cardNumber;
        this.last4 = last4(cardNumber);
        this.displayName = (displayName == null || displayName.isBlank())
                ? "Карта ****" + this.last4
                : displayName.trim();
        this.currency = "RUB";
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

    public String getCardNumber() {
        return cardNumber;
    }

    public String getLast4() {
        return last4;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isArchived() {
        return archived;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static String last4(String cardNumber) {
        String digits = cardNumber == null ? "" : cardNumber.replaceAll("\\D", "");
        return digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
    }
}
