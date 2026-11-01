package ru.sviridov.wallet.web.dto;

import java.time.Instant;

public record AuditResponse(
        Long id,
        Instant at,
        String actorKind,
        Long actorId,
        String entity,
        Long entityId,
        String action,
        String username,
        String ip) {
}
