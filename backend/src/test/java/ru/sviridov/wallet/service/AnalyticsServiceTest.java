package ru.sviridov.wallet.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.sviridov.wallet.domain.Category;
import ru.sviridov.wallet.domain.CategoryRepository;
import ru.sviridov.wallet.domain.Transaction;
import ru.sviridov.wallet.domain.TransactionRepository;
import ru.sviridov.wallet.web.dto.AnalyticsResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(transactionRepository, categoryRepository);
    }

    private static Transaction tx(LocalDate date, String type, String amount, Long categoryId) {
        Transaction t = new Transaction(1L, null, null, date, null, null, new BigDecimal(amount), type, null);
        t.setCategoryId(categoryId);
        return t;
    }

    @Test
    void computesKpisExcludingTransfersExcludedAndOutOfRange() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        Transaction debit = tx(LocalDate.of(2026, 1, 10), "debit", "100.00", 1L);
        Transaction credit = tx(LocalDate.of(2026, 1, 11), "credit", "250.00", null);
        Transaction transfer = tx(LocalDate.of(2026, 1, 12), "debit", "50.00", null);
        transfer.setIsTransfer(true);
        Transaction excluded = tx(LocalDate.of(2026, 1, 13), "credit", "30.00", null);
        excluded.setIsExcluded(true);
        Transaction outOfRange = tx(LocalDate.of(2026, 2, 1), "credit", "999.00", null);

        when(transactionRepository.findActiveByUserId(1L))
                .thenReturn(List.of(debit, credit, transfer, excluded, outOfRange));
        when(categoryRepository.findByUserIdOrderByNameAsc(1L)).thenReturn(List.of());

        AnalyticsResponse r = service.analytics(1L, from, to);

        assertThat(r.income()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(r.expense()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(r.balance()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void fillsEveryMonthInRange() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        Transaction jan = tx(LocalDate.of(2026, 1, 5), "debit", "10.00", null);

        when(transactionRepository.findActiveByUserId(1L)).thenReturn(List.of(jan));
        when(categoryRepository.findByUserIdOrderByNameAsc(1L)).thenReturn(List.of());

        AnalyticsResponse r = service.analytics(1L, from, to);

        assertThat(r.monthly()).hasSize(3);
        assertThat(r.monthly().get(0).month()).isEqualTo("2026-01");
        assertThat(r.monthly().get(0).expense()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(r.monthly().get(1).month()).isEqualTo("2026-02");
        assertThat(r.monthly().get(1).expense()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(r.monthly().get(2).month()).isEqualTo("2026-03");
        assertThat(r.monthly().get(2).expense()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void groupsExpensesByCategorySortedDescAndSkipsIncome() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        Transaction cat1 = tx(LocalDate.of(2026, 1, 5), "debit", "30.00", 1L);
        Transaction uncat = tx(LocalDate.of(2026, 1, 6), "debit", "70.00", null);
        Transaction credit = tx(LocalDate.of(2026, 1, 7), "credit", "500.00", 1L);

        Category category = mock(Category.class);
        when(category.getId()).thenReturn(1L);
        when(category.getName()).thenReturn("Продукты");

        when(transactionRepository.findActiveByUserId(1L)).thenReturn(List.of(cat1, uncat, credit));
        when(categoryRepository.findByUserIdOrderByNameAsc(1L)).thenReturn(List.of(category));

        AnalyticsResponse r = service.analytics(1L, from, to);

        assertThat(r.categories()).hasSize(2);
        assertThat(r.categories().get(0).categoryId()).isNull();
        assertThat(r.categories().get(0).categoryName()).isEqualTo("Без категории");
        assertThat(r.categories().get(0).amount()).isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(r.categories().get(1).categoryId()).isEqualTo(1L);
        assertThat(r.categories().get(1).categoryName()).isEqualTo("Продукты");
        assertThat(r.categories().get(1).amount()).isEqualByComparingTo(new BigDecimal("30.00"));
    }
}
