package ru.sviridov.wallet.web;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.sviridov.wallet.security.AppUser;
import ru.sviridov.wallet.service.AnalyticsService;
import ru.sviridov.wallet.web.dto.AnalyticsResponse;

import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Aggregates the user's transactions for the dashboard. When omitted, the
     * period defaults to the trailing 12 calendar months (first day of the month
     * 11 months ago .. today). Amounts exclude transfers and excluded rows.
     */
    @GetMapping
    public AnalyticsResponse get(
            @AuthenticationPrincipal AppUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : YearMonth.now().minusMonths(11).atDay(1);
        if (start.isAfter(end)) {
            throw new BadRequestException("Параметр from не может быть позже to");
        }
        return analyticsService.analytics(user.getId(), start, end);
    }
}
