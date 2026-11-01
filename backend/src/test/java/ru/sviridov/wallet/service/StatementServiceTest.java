package ru.sviridov.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.sviridov.wallet.domain.Card;
import ru.sviridov.wallet.domain.Category;
import ru.sviridov.wallet.domain.CategoryRepository;
import ru.sviridov.wallet.domain.Rule;
import ru.sviridov.wallet.domain.RuleRepository;
import ru.sviridov.wallet.domain.Statement;
import ru.sviridov.wallet.domain.StatementRepository;
import ru.sviridov.wallet.domain.Transaction;
import ru.sviridov.wallet.domain.TransactionRepository;
import ru.sviridov.wallet.parser.ParsedRow;
import ru.sviridov.wallet.parser.ParsedStatement;
import ru.sviridov.wallet.parser.PdfBoxStatementParser;
import ru.sviridov.wallet.parser.ScreenshotParser;
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
    private PdfBoxStatementParser parser;
    @Mock
    private ScreenshotParser screenshotParser;
    @Mock
    private StatementGapService statementGapService;

    private StatementService service;

    @BeforeEach
    void setUp() {
        lenient().when(statementRepository.save(any(Statement.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(ruleRepository.findByUserIdOrderByPriorityDescIdAsc(anyLong())).thenReturn(List.of());
        lenient().when(categoryRepository.findByUserIdOrderByNameAsc(anyLong())).thenReturn(List.of());
        Card resolvedCard = mock(Card.class);
        lenient().when(resolvedCard.getId()).thenReturn(5L);
        lenient().when(cardService.findOrCreateByNumberOrNull(anyLong(), anyString()))
                .thenReturn(resolvedCard);
        CategorizationEngine engine = new CategorizationEngine(new ObjectMapper(), new TextNormalizer());
        service = new StatementService(cardService, statementRepository, transactionRepository,
                ruleRepository, categoryRepository, parser, screenshotParser, engine,
                new StatementDraftStore(), statementGapService);
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
        return service.preview(userId, new byte[]{1}, false);
    }

    @Test
    void autoCreatesCardFromStatementNumber() throws Exception {
        Long userId = 1L;
        ParsedStatement parsed = statementOf(debit(1, "2026-09-05", "100.00", "Магазин А"));

        doPreview(userId, parsed);

        verify(cardService).findOrCreateByNumberOrNull(userId, "2202XXXXXXXX1234");
    }

    @Test
    void confirmAcceptsManuallyEnteredCardNumber() throws Exception {
        Long userId = 1L;
        ParsedStatement parsed = new ParsedStatement(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                null, "1234567890", "40817810000000000000",
                1, List.of(debit(1, "2026-09-05", "100.00", "Магазин А")), List.of());

        // The parser found no card number: the preview still works, card-less.
        StatementPreviewResponse preview = doPreview(userId, parsed);
        assertThat(preview.cardNumber()).isNull();
        assertThat(preview.transactions()).hasSize(1);

        when(cardService.findOrCreateByNumber(userId, "2202XXXXXXXX9999")).thenAnswer(inv -> {
            Card manualCard = mock(Card.class);
            when(manualCard.getId()).thenReturn(9L);
            return manualCard;
        });

        StatementImportResponse resp = service.confirm(userId, preview.draftId(), "2202XXXXXXXX9999");

        assertThat(resp.importedCount()).isEqualTo(1);
        verify(cardService).findOrCreateByNumber(userId, "2202XXXXXXXX9999");
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

        StatementImportResponse resp = service.confirm(userId, preview.draftId(), null);

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

    @Test
    void editDraftTransactionUpdatesOperationAmountAndDate() throws Exception {
        Long userId = 1L;
        ParsedStatement parsed = statementOf(debit(1, "2026-09-05", "100.00", "Магазин А"));

        StatementPreviewResponse preview = doPreview(userId, parsed);
        assertThat(preview.transactions().get(0).operation()).isEqualTo("Магазин А");

        StatementPreviewResponse edited = service.editDraftTransaction(
                userId, preview.draftId(), 1, "Новое имя", new BigDecimal("150.50"),
                LocalDate.of(2026, 9, 6));

        assertThat(edited.transactions()).hasSize(1);
        assertThat(edited.transactions().get(0).operation()).isEqualTo("Новое имя");
        assertThat(edited.transactions().get(0).amount()).isEqualByComparingTo("150.50");
        assertThat(edited.transactions().get(0).date()).isEqualTo(LocalDate.of(2026, 9, 6));
    }

    @Test
    void addDraftTransactionAddsStatementRow() throws Exception {
        Long userId = 1L;
        ParsedStatement parsed = statementOf(debit(1, "2026-09-05", "100.00", "Магазин А"));

        StatementPreviewResponse preview = doPreview(userId, parsed);
        assertThat(preview.validCount()).isEqualTo(1);

        StatementPreviewResponse added = service.addDraftTransaction(
                userId, preview.draftId(), "Новая операция", new BigDecimal("250.00"), "credit",
                LocalDate.of(2026, 9, 7));

        assertThat(added.validCount()).isEqualTo(2);
        assertThat(added.transactions()).hasSize(2);
        assertThat(added.transactions().get(1).operation()).isEqualTo("Новая операция");
        assertThat(added.transactions().get(1).amount()).isEqualByComparingTo("250.00");
        assertThat(added.transactions().get(1).type()).isEqualTo("credit");
        assertThat(added.transactions().get(1).date()).isEqualTo(LocalDate.of(2026, 9, 7));
    }

    @Test
    void deleteStatementHardDeletesItsTransactionsAndRemovesStatement() {
        Long userId = 1L;
        Long statementId = 42L;
        Long cardId = 7L;

        Statement statement = mock(Statement.class);
        when(statement.getCardId()).thenReturn(cardId);
        when(statementRepository.findById(statementId)).thenReturn(Optional.of(statement));
        when(cardService.get(userId, cardId)).thenReturn(mock(Card.class));

        Transaction tx1 = new Transaction(userId, cardId, 1, LocalDate.of(2026, 9, 5), "A", "p",
                new BigDecimal("10.00"), "debit", "fp1");
        Transaction tx2 = new Transaction(userId, cardId, 2, LocalDate.of(2026, 9, 6), "B", "p",
                new BigDecimal("20.00"), "debit", "fp2");
        when(transactionRepository.findByStatementId(statementId)).thenReturn(List.of(tx1, tx2));

        int deleted = service.deleteStatement(userId, statementId);

        assertThat(deleted).isEqualTo(2);
        verify(transactionRepository).deleteAll(List.of(tx1, tx2));
        verify(statementRepository).deleteById(statementId);
        verify(statementGapService).detectGaps(cardId);
    }

    @SuppressWarnings("unchecked")
    @Test
    void screenshotImportMarksTransactionsAsScreenshotSource() throws Exception {
        Long userId = 1L;
        ParsedStatement parsed = statementOf(debit(1, "2026-09-05", "100.00", "Магазин А"));
        when(screenshotParser.parse(any(byte[].class))).thenReturn(parsed);
        when(screenshotParser.version()).thenReturn("screenshot-1.0");
        when(transactionRepository.findExistingFingerprints(anyCollection())).thenReturn(Set.of());

        StatementPreviewResponse preview = service.preview(userId, new byte[]{1}, true);
        assertThat(preview.transactions()).hasSize(1);

        service.confirm(userId, preview.draftId(), null);

        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getSource()).isEqualTo("screenshot");
    }
}
