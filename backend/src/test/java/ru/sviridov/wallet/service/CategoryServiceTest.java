package ru.sviridov.wallet.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.sviridov.wallet.domain.Category;
import ru.sviridov.wallet.domain.CategoryRepository;
import ru.sviridov.wallet.web.BadRequestException;
import ru.sviridov.wallet.web.NotFoundException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validation and side effects of the category service: the emoji allow-list,
 * the lazily seeded system category and the delete cascade orchestration.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private TransactionService transactionService;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categoryRepository, transactionService);
        lenient().when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createUsesFallbackIconWhenBlank() {
        Category created = service.create(USER_ID, "Продукты", null, "  ");

        assertThat(created.getIcon()).isEqualTo("📦");
        assertThat(created.getName()).isEqualTo("Продукты");
        assertThat(created.isExcluded()).isFalse();
    }

    @Test
    void createRejectsIconOutsideAllowList() {
        assertThatThrownBy(() -> service.create(USER_ID, "Продукты", null, "🦄"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Недопустимый значок категории");

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void createAcceptsSystemExcludedIcon() {
        Category created = service.create(USER_ID, "Отпуск", null, "🚫");
        assertThat(created.getIcon()).isEqualTo("🚫");
    }

    @Test
    void createRejectsUnknownParent() {
        when(categoryRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(USER_ID, "Такси", 99L, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Родительская категория не найдена");
    }

    @Test
    void listSeedsTheSystemCategoryOnce() {
        when(categoryRepository.existsByUserIdAndIsExcludedTrue(USER_ID)).thenReturn(false);
        when(categoryRepository.findByUserIdOrderByNameAsc(USER_ID)).thenReturn(List.of());

        service.list(USER_ID);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        Category seeded = captor.getValue();
        assertThat(seeded.getName()).isEqualTo("исключено");
        assertThat(seeded.isExcluded()).isTrue();
        assertThat(seeded.getIcon()).isEqualTo("🚫");
    }

    @Test
    void listDoesNotSeedTwice() {
        when(categoryRepository.existsByUserIdAndIsExcludedTrue(USER_ID)).thenReturn(true);
        when(categoryRepository.findByUserIdOrderByNameAsc(USER_ID)).thenReturn(List.of());

        service.list(USER_ID);

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void deleteRefusesTheSystemCategory() {
        Category excluded = new Category(USER_ID, null, "исключено", "🚫", true);
        when(categoryRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.of(excluded));

        assertThatThrownBy(() -> service.delete(USER_ID, 7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Системная категория «исключено» не может быть удалена");
    }

    /**
     * The flush matters: the DB cascade (rules removed, transactions detached)
     * has to run before re-categorisation, otherwise the feed still shows the
     * deleted category.
     */
    @Test
    void deleteFlushesBeforeRecategorizing() {
        Category category = new Category(USER_ID, null, "Продукты", "🛒", false);
        when(categoryRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.of(category));

        service.delete(USER_ID, 7L);

        var order = org.mockito.Mockito.inOrder(categoryRepository, transactionService);
        order.verify(categoryRepository).delete(category);
        order.verify(categoryRepository).flush();
        order.verify(transactionService).reCategorizeAll(USER_ID);
    }

    @Test
    void updateRejectsUnknownCategory() {
        when(categoryRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER_ID, 7L, "Продукты", null, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Категория не найдена");
    }
}
