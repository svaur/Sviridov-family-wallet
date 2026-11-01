package ru.sviridov.wallet.web.dto;

import java.time.Instant;

public record ServerStatusResponse(
        String version,
        long uptimeSeconds,
        String dbStatus,
        long userCount,
        long adminCount,
        long transactionCount,
        long statementCount,
        long cardCount,
        long categoryCount,
        long ruleCount,
        Instant lastImportAt,
        long jvmMaxBytes,
        long jvmUsedBytes,
        long diskTotalBytes,
        long diskUsableBytes) {
}
