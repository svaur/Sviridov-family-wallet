package ru.sviridov.wallet.web.dto;

import java.util.List;

/**
 * Result of a confirmed import. {@code statementId} is {@code null} when every
 * row was already imported (a fully duplicate re-import creates no statement).
 */
public record StatementImportResponse(
        Long statementId,
        String status,
        int rowCountRaw,
        int importedCount,
        int skippedDuplicates,
        List<String> warnings,
        List<String> unrecognizedRows) {
}
