package ru.sviridov.wallet.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Step 1/2 payload of the upload wizard: parsed metadata + categorised preview
 * rows. Stateless — the rows are held by the frontend between the preview,
 * categorise and confirm calls, so there is no draft id.
 */
public record StatementPreviewResponse(
        String parserVersion,
        LocalDate periodFrom,
        LocalDate periodTo,
        String cardNumber,
        String contract,
        String account,
        int rowCountRaw,
        int validCount,
        List<String> warnings,
        List<String> unrecognizedRows,
        List<PreviewTransaction> transactions) {

    public record PreviewTransaction(
            int rowNumber,
            LocalDate date,
            String operation,
            String purpose,
            BigDecimal amount,
            String type,
            Long categoryId,
            String categoryName,
            boolean categoryManual) {
    }
}
