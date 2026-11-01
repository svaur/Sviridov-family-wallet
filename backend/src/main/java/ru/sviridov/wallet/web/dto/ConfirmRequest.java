package ru.sviridov.wallet.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Final payload of the upload wizard. The frontend keeps the parsed rows while
 * the user edits them, then sends the whole edited set back here together with
 * the parsed metadata on confirm.
 */
public record ConfirmRequest(
        @NotBlank @Size(max = 20) String cardNumber,
        @NotBlank @Size(max = 40) String parserVersion,
        LocalDate periodFrom,
        LocalDate periodTo,
        @Size(max = 120) String contract,
        @Size(max = 40) String account,
        int rowCountRaw,
        List<String> warnings,
        List<String> unrecognizedRows,
        List<StatementPreviewResponse.PreviewTransaction> transactions) {
}
