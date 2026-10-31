package ru.sviridov.wallet.web.dto;

import ru.sviridov.wallet.domain.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponse(Long id, LocalDate date, String operation, String purpose,
                                  BigDecimal amount, String type,
                                  Long categoryId, String categoryName, String source, Long cardId,
                                  boolean isTransfer, String comment) {

    public static TransactionResponse from(Transaction transaction, String categoryName) {
        return new TransactionResponse(transaction.getId(), transaction.getDate(), transaction.getOperation(),
                transaction.getPurpose(), transaction.getAmount(),
                transaction.getType(), transaction.getCategoryId(), categoryName, transaction.getSource(),
                transaction.getCardId(), transaction.isTransfer(), transaction.getComment());
    }
}
