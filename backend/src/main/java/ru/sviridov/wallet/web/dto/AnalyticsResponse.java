package ru.sviridov.wallet.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Server-side aggregation for the dashboard. All amounts are for the chosen
 * period with transfers and manually excluded transactions removed.
 * {@code balance} is {@code income - expense} (net flow for the period).
 */
public record AnalyticsResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal balance,
        List<MonthlyPoint> monthly,
        List<CategoryPoint> categories) {

    /** One point per calendar month in the period; month is "yyyy-MM". */
    public record MonthlyPoint(String month, BigDecimal income, BigDecimal expense) {
    }

    /** Expense total per category; {@code categoryId} is null for uncategorized. */
    public record CategoryPoint(Long categoryId, String categoryName, BigDecimal amount) {
    }
}
