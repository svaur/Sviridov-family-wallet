package ru.sviridov.wallet.web.dto;

import java.util.List;

public record StatementImportResponse(
        Long statementId,
        String status,
        int rowCountRaw,
        int importedCount,
        int skippedDuplicates,
        List<String> warnings,
        List<String> unrecognizedRows) {
}
