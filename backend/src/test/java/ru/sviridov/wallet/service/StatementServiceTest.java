package ru.sviridov.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.sviridov.wallet.domain.Card;
import ru.sviridov.wallet.domain.Category;
import ru.sviridov.wallet.domain.CategoryRepository;
import ru.sviridov.wallet.domain.Rule;
import ru.sviridov.wallet.domain.RuleRepository;
import ru.sviridov.wallet.domain.Statement;
import ru.sviridov.wallet.domain.StatementRepository;
import ru.sviridov.wallet.domain.TransactionRepository;
import ru.sviridov.wallet.parser.ParsedRow;
import ru.sviridov.wallet.parser.ParsedStatement;
import ru.sviridov.wallet.parser.StatementParser;
import ru.sviridov.wallet.web.dto.StatementImportResponse;
import ru.sviridov.wallet.web.dto.StatementPreviewResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    @Mock
    private CardService cardService;
    @Mock
    private StatementRepository statementRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private RuleRepository ruleRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private StatementParser parser;

    private StatementService service;

    @BeforeEach
    void setUp() {
        lenient().when(statementRepository.save(any(Statement.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(ruleRepository.findByUserIdOrderByPriorityDescIdAsc(anyLong())).thenReturn(List.of());
        lenient().when(categoryRepository.findByUserIdOrderByNameAsc(anyLong())).thenReturn(List.of());
        lenient().when(cardService.findOrCreateByNumber(anyLong(), anyString()))
                .thenAnswer(inv -> new Card(inv.getArgument(0), inv.getArgument(1), "Main"));
        CategorizationEngine engine = new CategorizationEngine(new ObjectMapper(), new TextNormalizer());
        service = new StatementService(cardService, statementRepository, transactionRepository,
                ruleRepository, categoryRepository, parser, engine, new StatementDraftStore());
    }

    private static ParsedStatement statementOf(ParsedRow... rows) {
        return new ParsedStatement(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                "2202XXXXXXXX1234", "1234567890", "40817810000000000000",
                rows.length, List.of(rows), List.of());
    }

    private static ParsedRow debit(int num, String date, String amount, String merchant) {
        return new ParsedRow(num, LocalDate.parse(date), merchant, new BigDecimal(amount), null,
                "Списание по транзакции. Безналичная оплата покупки");
    }

    private StatementPreviewResponse doPreview(Long userId, ParsedStatement parsed) throws Exception {
        when(parser.parse(any(byte[].class))).thenReturn(parsed);
        return service.preview(userId, new byte[]{1});
    }

    @Test
    void autoCreatesCardFromStatementNumber() throws Exception {
        Long userId = 1L;
        ParsedStatement parsed = statementOf(debit(1, "2026-09-05", "100.00", "Магазин А"));

        doPreview(userId, parsed);

        verify(cardService).findOrCreateByNumber(userId, "2202XXXXXXXX1234");
    }

    @Test
    void previewShowsCategoriesAndConfirmImportsSkippingDuplicates() throws Exception {
        Long userId = 1L;
        ParsedStatement parsed = statementOf(
                debit(1, "2026-09-05", "100.00", "Магазин А"),
                debit(2, "2026-09-06", "200.00", "Магазин Б"));
        when(parser.version()).thenReturn("test-1.0");
        when(transactionRepository.findExistingFingerprints(anyCollection())).thenAnswer(inv -> {
            Collection<String> fps = inv.getArgument(0);
            return fps.stream().findFirst().map(Set::of).orElse(Set.of());
        });

        StatementPreviewResponse preview = doPreview(userId, parsed);
        assertThat(preview.validCount()).isEqualTo(2);
        assertThat(preview.transactions()).hasSize(2);
        assertThat(preview.skippedDuplicates()).isEqualTo(1);

        StatementImportResponse resp = service.confirm(userId, preview.draftId());

        assertThat(resp.importedCount()).isEqualTo(1);
        assertThat(resp.skippedDuplicates()).isEqualTo(1);
        assertThat(resp.status()).isEqualTo("OK");
        assertThat(resp.rowCountRaw()).isEqualTo(2);
        verify(statementRepository).save(any(Statement.class));
        verify(transactionRepository).saveAll(anyList());
    }

    @Test
    void flagsRowWithBothColumnsAsUnrecognized() throws Exception {
        Long userId = 1L;
        ParsedRow bad = new ParsedRow(1, LocalDate.of(2026, 9, 5), "X",
                new BigDecimal("10.00"), new BigDecimal("20.00"), "p");
        ParsedStatement parsed = statementOf(bad);

        StatementPreviewResponse preview = doPreview(userId, parsed);

        assertThat(preview.validCount()).isEqualTo(0);
        assertThat(preview.transactions()).isEmpty();
        assertThat(preview.unrecognizedRows()).hasSize(1);
    }

    @Test
    void recategorizeAppliesCurrentRules() throws Exception {
        Long userId = 1L;
        ParsedStatement parsed = statementOf(debit(1, "2026-09-05", "100.00", "Магазин"));
        when(parser.version()).thenReturn("test-1.0");

        StatementPreviewResponse preview = doPreview(userId, parsed);
        assertThat(preview.transactions().get(0).categoryId()).isNull();
        // Counterparty name is taken from the operation column, not the payment purpose.
        assertThat(preview.transactions().get(0).operation()).isEqualTo("Магазин");

        Category category = mock(Category.class);
        when(category.getId()).thenReturn(5L);
        when(category.getName()).thenReturn("Продукты");
        Rule rule = new Rule(userId, "продукты", "{\"operation_contains\":\"магазин\"}", 5L, 100, false);
        when(ruleRepository.findByUserIdOrderByPriorityDescIdAsc(userId)).thenReturn(List.of(rule));
        when(categoryRepository.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(category));

        StatementPreviewResponse re = service.recategorize(userId, preview.draftId());

        assertThat(re.transactions().get(0).categoryId()).isEqualTo(5L);
        assertThat(re.transactions().get(0).categoryName()).isEqualTo("Продукты");
    }

    @Test
    void removeTransactionRemovesRowFromDraft() throws Exception {
        Long userId = 1L;
        ParsedStatement parsed = statementOf(
                debit(1, "2026-09-05", "100.00", "Магазин А"),
                debit(2, "2026-09-06", "200.00", "Магазин Б"));

        StatementPreviewResponse preview = doPreview(userId, parsed);
        assertThat(preview.validCount()).isEqualTo(2);

        StatementPreviewResponse after = service.removeTransaction(userId, preview.draftId(), 1);

        assertThat(after.validCount()).isEqualTo(1);
        assertThat(after.transactions()).hasSize(1);
        assertThat(after.transactions().get(0).rowNumber()).isEqualTo(2);
    }

    @Test
    void assignDraftCategoryMarksManualAndSurvivesRecategorize() throws Exception {
        Long userId = 1L;
        ParsedStatement parsed = statementOf(debit(1, "2026-09-05", "100.00", "Магазин"));

        StatementPreviewResponse preview = doPreview(userId, parsed);
        assertThat(preview.transactions().get(0).categoryId()).isNull();

        Category category = mock(Category.class);
        when(category.getId()).thenReturn(5L);
        when(category.getName()).thenReturn("Продукты");
        when(categoryRepository.findByIdAndUserId(5L, userId)).thenReturn(Optional.of(category));
        when(categoryRepository.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(category));

        StatementPreviewResponse assigned = service.assignDraftCategory(userId, preview.draftId(), 1, 5L);
        assertThat(assigned.transactions().get(0).categoryId()).isEqualTo(5L);

        // A matching rule would categorise to 9; the manual assignment must win.
        Rule rule = new Rule(userId, "продукты", "{\"operation_contains\":\"магазин\"}", 9L, 100, false);
        when(ruleRepository.findByUserIdOrderByPriorityDescIdAsc(userId)).thenReturn(List.of(rule));

        StatementPreviewResponse re = service.recategorize(userId, preview.draftId());
        assertThat(re.transactions().get(0).categoryId()).isEqualTo(5L);
    }
}
