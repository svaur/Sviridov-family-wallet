package ru.sviridov.wallet.parser;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single transaction row extracted from a statement. Exactly one of
 * {@link #expense()} / {@link #income()} is expected to be non-null (XOR);
 * the import pipeline validates this invariant.
 */
public record ParsedRow(
        int rowNumber,
        LocalDate date,
        String operation,
        BigDecimal expense,
        BigDecimal income,
        String purpose) {

    /** "debit" when the amount came from the Расход column, "credit" for Приход. */
    public String type() {
        return expense != null ? "debit" : "credit";
    }

    /** The non-null amount (the value of whichever column was filled). */
    public BigDecimal amount() {
        return expense != null ? expense : income;
    }
}
