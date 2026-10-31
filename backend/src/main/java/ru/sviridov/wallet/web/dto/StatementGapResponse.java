package ru.sviridov.wallet.web.dto;

import ru.sviridov.wallet.domain.StatementGap;

import java.time.Instant;
import java.time.LocalDate;

public record StatementGapResponse(Long id, Long cardId, LocalDate gapFrom, LocalDate gapTo, Instant detectedAt) {

    public static StatementGapResponse from(StatementGap gap) {
        return new StatementGapResponse(gap.getId(), gap.getCardId(), gap.getGapFrom(), gap.getGapTo(),
                gap.getDetectedAt());
    }
}
