package ru.sviridov.wallet.web.dto;

import ru.sviridov.wallet.domain.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponse(Long id, LocalDate date, String operation, String purpose,
                                  String merchantNormalized, BigDecimal amount, String type,
                                  Long categoryId, String categoryName, String source, Long cardId) {

    public static TransactionResponse from(Transaction transaction, String categoryName) {
        return new TransactionResponse(transaction.getId(), transaction.getDate(), transaction.getOperation(),
                transaction.getPurpose(), transaction.getMerchantNormalized(), transaction.getAmount(),
                transaction.getType(), transaction.getCategoryId(), categoryName, transaction.getSource(),
                transaction.getCardId());
    }
}
