package ru.sviridov.wallet.web.dto;

import ru.sviridov.wallet.domain.Card;

import java.time.Instant;

public record CardResponse(
        Long id,
        String cardNumber,
        String displayName,
        String last4,
        String currency,
        boolean archived,
        Instant createdAt) {

    public static CardResponse from(Card card) {
        return new CardResponse(
                card.getId(),
                card.getCardNumber(),
                card.getDisplayName(),
                card.getLast4(),
                card.getCurrency(),
                card.isArchived(),
                card.getCreatedAt());
    }
}
