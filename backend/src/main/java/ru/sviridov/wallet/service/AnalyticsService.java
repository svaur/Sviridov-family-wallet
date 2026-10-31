package ru.sviridov.wallet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.Category;
import ru.sviridov.wallet.domain.CategoryRepository;
import ru.sviridov.wallet.domain.Transaction;
import ru.sviridov.wallet.domain.TransactionRepository;
import ru.sviridov.wallet.web.dto.AnalyticsResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public AnalyticsService(TransactionRepository transactionRepository, CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse analytics(Long userId, LocalDate from, LocalDate to) {
        List<Transaction> txs = transactionRepository.findActiveByUserId(userId).stream()
                .filter(t -> !t.getDate().isBefore(from) && !t.getDate().isAfter(to))
                .filter(t -> !t.isTransfer() && !t.isExcluded())
                .toList();

        BigDecimal income = sum(txs, "credit");
        BigDecimal expense = sum(txs, "debit");
        Map<Long, String> categoryNames = categoryRepository.findByUserIdOrderByNameAsc(userId).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        return new AnalyticsResponse(
                from, to,
                income, expense, income.subtract(expense),
                monthly(txs, from, to),
                categories(txs, categoryNames));
    }

    private static BigDecimal sum(List<Transaction> txs, String type) {
        return txs.stream()
                .filter(t -> type.equals(t.getType()))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<AnalyticsResponse.MonthlyPoint> monthly(List<Transaction> txs, LocalDate from, LocalDate to) {
        Map<YearMonth, BigDecimal[]> byMonth = new LinkedHashMap<>();
        for (Transaction t : txs) {
            BigDecimal[] acc = byMonth.computeIfAbsent(YearMonth.from(t.getDate()),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if ("credit".equals(t.getType())) {
                acc[0] = acc[0].add(t.getAmount());
            } else {
                acc[1] = acc[1].add(t.getAmount());
            }
        }

        List<AnalyticsResponse.MonthlyPoint> points = new ArrayList<>();
        YearMonth ym = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        while (!ym.isAfter(end)) {
            BigDecimal[] acc = byMonth.getOrDefault(ym, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            points.add(new AnalyticsResponse.MonthlyPoint(ym.toString(), acc[0], acc[1]));
            ym = ym.plusMonths(1);
        }
        return points;
    }

    private static List<AnalyticsResponse.CategoryPoint> categories(List<Transaction> txs, Map<Long, String> categoryNames) {
        Map<Long, BigDecimal> byCategory = new LinkedHashMap<>();
        for (Transaction t : txs) {
            if (!"debit".equals(t.getType())) {
                continue;
            }
            byCategory.merge(t.getCategoryId(), t.getAmount(), BigDecimal::add);
        }
        return byCategory.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(e -> new AnalyticsResponse.CategoryPoint(
                        e.getKey(),
                        e.getKey() == null ? "Без категории" : categoryNames.getOrDefault(e.getKey(), "Без категории"),
                        e.getValue()))
                .toList();
    }
}
