package ru.sviridov.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.sviridov.wallet.domain.Category;
import ru.sviridov.wallet.domain.CategoryRepository;
import ru.sviridov.wallet.domain.Rule;
import ru.sviridov.wallet.domain.RuleRepository;
import ru.sviridov.wallet.web.BadRequestException;
import ru.sviridov.wallet.web.NotFoundException;
import ru.sviridov.wallet.web.dto.RuleRequest;
import ru.sviridov.wallet.web.dto.RuleResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Input validation and side effects of the rules service. Matching itself is
 * covered by {@code CategorizationEngineTest}; here we only check what the
 * service agrees to store and what it triggers.
 */
@ExtendWith(MockitoExtension.class)
class RuleServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CATEGORY_ID = 5L;

    @Mock
    private RuleRepository ruleRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private TransactionService transactionService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TextNormalizer textNormalizer = new TextNormalizer();

    private RuleService service;

    @BeforeEach
    void setUp() {
        service = new RuleService(ruleRepository, categoryRepository, transactionService,
                objectMapper, textNormalizer);
        lenient().when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------ validation

    @Test
    void createRejectsUnknownConditionKey() {
        categoryExists();
        RuleRequest request = new RuleRequest("Продукты",
                Map.of("merchant_contains", "пятёрочка"), CATEGORY_ID, null);

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Неизвестное условие правила: merchant_contains");

        verify(ruleRepository, never()).save(any(Rule.class));
    }

    @Test
    void createRejectsMissingParams() {
        categoryExists();

        assertThatThrownBy(() -> service.create(USER_ID, new RuleRequest("Пустое", null, CATEGORY_ID, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Задайте хотя бы одно условие правила");
    }

    @Test
    void createRejectsEmptyParamsMap() {
        categoryExists();

        assertThatThrownBy(() -> service.create(USER_ID, new RuleRequest("Пустое", Map.of(), CATEGORY_ID, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Задайте хотя бы одно условие правила");
    }

    @Test
    void createRejectsBlankContainsValue() {
        categoryExists();
        RuleRequest request = new RuleRequest("Пустое",
                Map.of("operation_contains", "   "), CATEGORY_ID, null);

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Условие 'operation_contains' не может быть пустым");
    }

    @Test
    void createRejectsInvalidRegex() {
        categoryExists();
        RuleRequest request = new RuleRequest("Кривое",
                Map.of("operation_regex", "(["), CATEGORY_ID, null);

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageStartingWith("Некорректное регулярное выражение");
    }

    @Test
    void createRejectsMinGreaterThanMax() {
        categoryExists();
        RuleRequest request = new RuleRequest("Диапазон",
                Map.of("min", "500", "max", "100"), CATEGORY_ID, null);

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("'min' не может быть больше 'max'");
    }

    @Test
    void createRejectsNegativeMin() {
        categoryExists();
        RuleRequest request = new RuleRequest("Отрицательный",
                Map.of("min", "-1"), CATEGORY_ID, null);

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("'min' не может быть отрицательным");
    }

    @Test
    void createRejectsNonNumericAmount() {
        categoryExists();
        RuleRequest request = new RuleRequest("Сумма",
                Map.of("max", "много"), CATEGORY_ID, null);

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Некорректное число в 'max'");
    }

    @Test
    void createRejectsDayFromGreaterThanDayTo() {
        categoryExists();
        RuleRequest request = new RuleRequest("Дни",
                Map.of("day_from", 20, "day_to", 5), CATEGORY_ID, null);

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("'day_from' не может быть больше 'day_to'");
    }

    @Test
    void createRejectsDayOutsideMonth() {
        categoryExists();
        RuleRequest request = new RuleRequest("Дни",
                Map.of("day_from", 0), CATEGORY_ID, null);

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("'day_from' должен быть от 1 до 31");
    }

    @Test
    void createRejectsUnknownCategory() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(USER_ID,
                new RuleRequest("Продукты", Map.of("operation_contains", "пятёрочка"), CATEGORY_ID, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Категория не найдена");
    }

    @Test
    void createRejectsMissingCategoryId() {
        assertThatThrownBy(() -> service.create(USER_ID,
                new RuleRequest("Продукты", Map.of("operation_contains", "пятёрочка"), null, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Категория не найдена");
    }

    // --------------------------------------------------------- canonicalising

    @Test
    void createCanonicalisesContainsAndDefaultsPriority() {
        categoryExists();
        RuleRequest request = new RuleRequest("  Продукты  ",
                Map.of("operation_contains", "  ПРОДУКТЫ   ОНЛАЙН "), CATEGORY_ID, null);

        RuleResponse created = service.create(USER_ID, request);

        // Lowercased once on write, so matching never needs a case-insensitive path.
        assertThat(created.params().get("operation_contains")).isEqualTo("продукты онлайн");
        assertThat(created.priority()).isEqualTo(100);
        assertThat(created.name()).isEqualTo("Продукты");
        assertThat(created.autoLearned()).isFalse();
        verify(transactionService).reCategorizeAll(USER_ID);
    }

    @Test
    void createKeepsRegexAsWrittenAndStoresBounds() {
        categoryExists();
        RuleRequest request = new RuleRequest("Подписки",
                Map.of("purpose_regex", "ПОДПИСКА|ЯНДЕКС ПЛЮС", "min", "100", "max", "2000.50"),
                CATEGORY_ID, 300);

        RuleResponse created = service.create(USER_ID, request);

        // Regexes are compiled for validation but stored verbatim.
        assertThat(created.params().get("purpose_regex")).isEqualTo("ПОДПИСКА|ЯНДЕКС ПЛЮС");
        assertThat(created.params().get("min")).isNotNull();
        assertThat(created.params().get("max")).isNotNull();
        assertThat(created.priority()).isEqualTo(300);
    }

    @Test
    void createStoresBlankNameAsNull() {
        categoryExists();
        RuleRequest request = new RuleRequest("   ",
                Map.of("operation_contains", "пятёрочка"), CATEGORY_ID, null);

        assertThat(service.create(USER_ID, request).name()).isNull();
    }

    // ------------------------------------------------------------- mutations

    @Test
    void updateRejectsUnknownRule() {
        when(ruleRepository.findByIdAndUserId(9L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER_ID, 9L,
                new RuleRequest("Продукты", Map.of("operation_contains", "пятёрочка"), CATEGORY_ID, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Правило не найдено");
    }

    @Test
    void updateAppliesChangesAndRecategorises() {
        Rule rule = existingRule();
        when(ruleRepository.findByIdAndUserId(9L, USER_ID)).thenReturn(Optional.of(rule));
        categoryExists();

        RuleResponse updated = service.update(USER_ID, 9L, new RuleRequest("Аптека",
                Map.of("purpose_contains", "АПТЕКА"), CATEGORY_ID, 7));

        assertThat(updated.name()).isEqualTo("Аптека");
        assertThat(updated.params().get("purpose_contains")).isEqualTo("аптека");
        assertThat(updated.priority()).isEqualTo(7);
        verify(transactionService).reCategorizeAll(USER_ID);
    }

    @Test
    void deleteFlushesBeforeRecategorizing() {
        Rule rule = existingRule();
        when(ruleRepository.findByIdAndUserId(9L, USER_ID)).thenReturn(Optional.of(rule));

        service.delete(USER_ID, 9L);

        // Flush first: re-categorisation must not still see the deleted rule.
        var order = inOrder(ruleRepository, transactionService);
        order.verify(ruleRepository).delete(rule);
        order.verify(ruleRepository).flush();
        order.verify(transactionService).reCategorizeAll(USER_ID);
    }

    @Test
    void deleteRejectsUnknownRule() {
        when(ruleRepository.findByIdAndUserId(9L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_ID, 9L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Правило не найдено");
    }

    @Test
    void listMapsStoredRules() {
        when(ruleRepository.findByUserIdOrderByPriorityDescIdAsc(USER_ID)).thenReturn(List.of(existingRule()));

        List<RuleResponse> rules = service.list(USER_ID);

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).params().get("operation_contains")).isEqualTo("продукты");
    }

    // -------------------------------------------------------------- helpers

    private void categoryExists() {
        lenient().when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(new Category(USER_ID, null, "Продукты", "🛒", false)));
    }

    private static Rule existingRule() {
        return new Rule(USER_ID, "Продукты",
                "{\"operation_contains\":\"продукты\"}", CATEGORY_ID, 100, false);
    }
}
