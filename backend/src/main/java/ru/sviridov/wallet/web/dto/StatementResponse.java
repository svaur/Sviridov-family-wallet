package ru.sviridov.wallet.web.dto;

import ru.sviridov.wallet.domain.Statement;

import java.time.Instant;
import java.time.LocalDate;

public record StatementResponse(
        Long id,
        Long cardId,
        Instant uploadedAt,
        LocalDate periodFrom,
        LocalDate periodTo,
        String parserVersion,
        String status,
        Integer txnCount,
        Integer rowCountRaw,
        String cardNumberExtracted,
        String contract,
        String account) {

    public static StatementResponse from(Statement s) {
        return new StatementResponse(s.getId(), s.getCardId(), s.getUploadedAt(),
                s.getPeriodFrom(), s.getPeriodTo(), s.getParserVersion(), s.getStatus(),
                s.getTxnCount(), s.getRowCountRaw(), s.getCardNumberExtracted(),
                s.getContract(), s.getAccount());
    }
}
