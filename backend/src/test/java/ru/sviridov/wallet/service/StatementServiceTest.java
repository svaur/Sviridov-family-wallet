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
import ru.sviridov.wallet.web.dto.ConfirmRequest;
import ru.sviridov.wallet.web.dto.StatementImportResponse;
import ru.sviridov.wallet.web.dto.StatementPreviewResponse;

import java.io.InputStream;
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
import static org.mockito.Mockito.never;
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
        lenient().when(cardService.findOrCreateByNumber(anyLong(), anyString())).thenReturn(resolvedCard);
        lenient().when(cardService.findByLast4(anyLong(), anyString())).thenReturn(Optional.empty());
        CategorizationEngine engine = new CategorizationEngine(new ObjectMapper(), new TextNormalizer());
        service = new StatementService(cardService, statementRepository, transactionRepository,
                ruleRepository, categoryRepository, parser, screenshotParser, engine,
                statementGapService);
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

    private static ConfirmRequest confirmRequest(StatementPreviewResponse preview, String cardNumber) {
        return new ConfirmRequest(
                cardNumber != null ? cardNumber : preview.cardNumber(),
                preview.parserVersion(), preview.periodFrom(), preview.periodTo(),
                preview.contract(), preview.account(), preview.rowCountRaw(),
                preview.warnings(), preview.unrecognizedRows(), preview.transactions());
    }

    private StatementPreviewResponse doPreview(Long userId, ParsedStatement parsed) throws Exception {
        when(parser.parse(any(byte[].class))).thenReturn(parsed);
        return service.preview(userId, new byte[]{1}, false);
    }

    @Test
    void previewParsesAndReturnsRowsWithoutCreatingCard() throws Exception {
        Long userId = 1L;
        ParsedStatement parsed = statementOf(
                debit(1, "2026-09-05", "100.00", "Магазин А"),
                debit(2, "2026-09-06", "200.00", "Магазин Б"));
        when(parser.version()).thenReturn("test-1.0");

        StatementPreviewResponse preview = doPreview(userId, parsed);

        assertThat(preview.parserVersion()).isEqualTo("test-1.0");
        assertThat(preview.validCount()).isEqualTo(2);
        assertThat(preview.transactions()).hasSize(2);
        assertThat(preview.cardNumber()).isEqualTo("2202XXXXXXXX1234");
        // Preview must NOT create a card — only a read-only lookup for the overlap check.
        verify(cardService).findByLast4(userId, "2202XXXXXXXX1234");
        verify(cardService, never()).findOrCreateByNumber(anyLong(), anyString());
    }

    @Test
    void previewParsesTestPdfFileAndReturnsAllRows() throws Exception {
        Long userId = 1L;
        byte[] pdf;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("TEST_PDF.pdf")) {
            assertThat(in).isNotNull();
            pdf = in.readAllBytes();
        }

        // Use the REAL PDF parser (not a stub) so the test exercises the same
        // parse→validate→categorize path as production — a regression guard for
        // the "IncorrectResultSizeDataAccessException" the preview used to throw.
        CategorizationEngine engine = new CategorizationEngine(new ObjectMapper(), new TextNormalizer());
        service = new StatementService(cardService, statementRepository, transactionRepository,
                ruleRepository, categoryRepository, new PdfBoxStatementParser(), screenshotParser, engine,
                statementGapService);

        StatementPreviewResponse preview = service.preview(userId, pdf, false);

        assertThat(preview.periodFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(preview.periodTo()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(preview.cardNumber()).isEqualTo("2202XXXXXXXX1234");
        assertThat(preview.rowCountRaw()).isEqualTo(12);
        assertThat(preview.validCount()).isEqualTo(12);
        assertThat(preview.transactions()).hasSize(12);
        assertThat(preview.unrecognizedRows()).isEmpty();

        StatementPreviewResponse.PreviewTransaction first = preview.transactions().get(0);
        assertThat(first.operation()).isEqualTo("Оплата через СБП ООО РОМАШКА");
        assertThat(first.type()).isEqualTo("debit");
        assertThat(first.amount()).isEqualByComparingTo(new BigDecimal("1234.50"));
    }

    @Test
    void categorizeAppliesCurrentRules() throws Exception {
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

        List<StatementPreviewResponse.PreviewTransaction> re = service.categorize(userId, preview.transactions());

        assertThat(re.get(0).categoryId()).isEqualTo(5L);
        assertThat(re.get(0).categoryName()).isEqualTo("Продукты");
    }

    @Test
    void categorizeKeepsManualCategoryEvenWhenRuleMatches() {
        Long userId = 1L;
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(5L);
        when(category.getName()).thenReturn("Продукты");
        Rule rule = new Rule(userId, "продукты", "{\"operation_contains\":\"магазин\"}", 9L, 100, false);
        when(ruleRepository.findByUserIdOrderByPriorityDescIdAsc(userId)).thenReturn(List.of(rule));
        when(categoryRepository.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(category));

        StatementPreviewResponse.PreviewTransaction manual = new StatementPreviewResponse.PreviewTransaction(
                1, LocalDate.of(2026, 9, 5), "Магазин", "Списание", new BigDecimal("100.00"),
                "debit", 5L, null, true);

        List<StatementPreviewResponse.PreviewTransaction> re = service.categorize(userId, List.of(manual));

        // Manual category 5 wins over the matching rule → 9.
        assertThat(re.get(0).categoryId()).isEqualTo(5L);
        assertThat(re.get(0).categoryName()).isEqualTo("Продукты");
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

        StatementImportResponse resp = service.confirm(userId, confirmRequest(preview, "2202XXXXXXXX9999"));

        assertThat(resp.importedCount()).isEqualTo(1);
        verify(cardService).findOrCreateByNumber(userId, "2202XXXXXXXX9999");
    }

    @Test
    void confirmImportsSkippingDuplicates() throws Exception {
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

        StatementImportResponse resp = service.confirm(userId, confirmRequest(preview, null));

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
        assertThat(preview.parserVersion()).isEqualTo("screenshot-1.0");
        assertThat(preview.transactions()).hasSize(1);

        service.confirm(userId, confirmRequest(preview, null));

        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getSource()).isEqualTo("screenshot");
    }
}
