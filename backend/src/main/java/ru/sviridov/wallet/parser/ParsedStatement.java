package ru.sviridov.wallet.parser;

import java.time.LocalDate;
import java.util.List;

/**
 * Structured result of parsing a statement PDF: header metadata plus the
 * recognised rows and the rows that could not be parsed.
 *
 * @param rowCountRaw      total number of data rows detected in the table
 * @param rows             successfully parsed rows
 * @param unrecognizedRows human-readable descriptions of rows that failed to parse
 */
public record ParsedStatement(
        LocalDate periodFrom,
        LocalDate periodTo,
        String cardNumber,
        String contract,
        String account,
        int rowCountRaw,
        List<ParsedRow> rows,
        List<String> unrecognizedRows) {
}
