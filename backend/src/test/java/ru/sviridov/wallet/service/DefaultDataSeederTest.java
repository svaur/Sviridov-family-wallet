package ru.sviridov.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.Category;
import ru.sviridov.wallet.domain.CategoryRepository;
import ru.sviridov.wallet.domain.Rule;
import ru.sviridov.wallet.domain.RuleRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultDataSeederTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private RuleRepository ruleRepository;

    private DefaultDataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DefaultDataSeeder(categoryRepository, ruleRepository, new ObjectMapper());
    }

    @Test
    void seedsDefaultsWhenEmpty() {
        when(categoryRepository.countByUserId(1L)).thenReturn(0L);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> inv.getArgument(0));

        seeder.ensureDefaults(1L);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository, times(4)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Category::getName)
                .containsExactly("Продукты", "Транспорт", "ЖКХ", "Аптека");
        verify(ruleRepository, times(36)).save(any(Rule.class));
    }

    @Test
    void skipsWhenAlreadySeeded() {
        when(categoryRepository.countByUserId(1L)).thenReturn(5L);

        seeder.ensureDefaults(1L);

        verify(categoryRepository, never()).save(any(Category.class));
        verify(ruleRepository, never()).save(any(Rule.class));
    }

    /**
     * Regression guard for the "cannot execute INSERT in a read-only transaction"
     * bug: {@code ensureDefaults} must run in its own writable transaction so that
     * seeding works when called from a {@code @Transactional(readOnly = true)}
     * service method (e.g. {@code CategoryService.list()} / {@code RuleService.list()}).
     */
    @Test
    void ensureDefaultsRunsInNewTransaction() throws NoSuchMethodException {
        Transactional annotation = DefaultDataSeeder.class
                .getMethod("ensureDefaults", Long.class)
                .getAnnotation(Transactional.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
