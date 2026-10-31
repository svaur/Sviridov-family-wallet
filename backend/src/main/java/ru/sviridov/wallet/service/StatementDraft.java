package ru.sviridov.wallet.service;

import ru.sviridov.wallet.domain.Transaction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * In-memory draft of a statement import held between the preview, re-categorise
 * and confirm steps of the upload wizard. Carries the parsed metadata and the
 * categorised (but not yet persisted) transactions.
 */
record StatementDraft(
        Long userId,
        Long cardId,
        String parserVersion,
        LocalDate periodFrom,
        LocalDate periodTo,
        String cardNumber,
        String contract,
        String account,
        int rowCountRaw,
        int validCount,
        List<Transaction> transactions,
        List<String> warnings,
        List<String> unrecognizedRows,
        Instant createdAt) {
}
