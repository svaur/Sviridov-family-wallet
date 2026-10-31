package ru.sviridov.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.sviridov.wallet.domain.Card;
import ru.sviridov.wallet.domain.CardRepository;
import ru.sviridov.wallet.domain.Category;
import ru.sviridov.wallet.domain.CategoryRepository;
import ru.sviridov.wallet.domain.Rule;
import ru.sviridov.wallet.domain.RuleRepository;
import ru.sviridov.wallet.domain.Transaction;
import ru.sviridov.wallet.domain.TransactionRepository;
import ru.sviridov.wallet.web.BadRequestException;
import ru.sviridov.wallet.web.NotFoundException;
import ru.sviridov.wallet.web.dto.TransactionRequest;
import ru.sviridov.wallet.web.dto.TransactionResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private RuleRepository ruleRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CardRepository cardRepository;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(ruleRepository.findByUserIdOrderByPriorityDescIdAsc(anyLong())).thenReturn(List.of());
        CategorizationEngine engine = new CategorizationEngine(new ObjectMapper(), new TextNormalizer());
        service = new TransactionService(transactionRepository, ruleRepository, categoryRepository,
                engine, cardRepository);
    }

    private static TransactionRequest request(String source, Long cardId) {
        return new TransactionRequest(source, cardId, LocalDate.of(2026, 10, 1),
                "debit", new BigDecimal("100.00"), "Магазин", null);
    }

    @Test
    void createCardSourceSetsSourceAndCard() {
        Long userId = 1L, cardId = 10L;
        when(cardRepository.findByIdAndUserId(cardId, userId))
                .thenReturn(Optional.of(new Card(userId, "Main", "Bank", "1234", "RUB")));

        TransactionResponse resp = service.create(userId, request("card", cardId));

        assertThat(resp.source()).isEqualTo("card");
        assertThat(resp.cardId()).isEqualTo(cardId);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("card");
        assertThat(captor.getValue().getCardId()).isEqualTo(cardId);
        assertThat(captor.getValue().getOperation()).isEqualTo("добавлено вручную");
    }

    @Test
    void createCashSourceNullsCard() {
        TransactionResponse resp = service.create(1L, request("cash", 99L));

        assertThat(resp.source()).isEqualTo("cash");
        assertThat(resp.cardId()).isNull();
    }

    @Test
    void createOtherSourceNullsCard() {
        TransactionResponse resp = service.create(1L, request("other", null));

        assertThat(resp.source()).isEqualTo("other");
        assertThat(resp.cardId()).isNull();
    }

    @Test
    void createCardSourceWithoutCardRejected() {
        assertThatThrownBy(() -> service.create(1L, request("card", null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createCardSourceUnknownCardRejected() {
        Long userId = 1L, cardId = 10L;
        when(cardRepository.findByIdAndUserId(cardId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(userId, request("card", cardId)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteSoftDeletesTransaction() {
        Long userId = 1L, txId = 5L;
        Transaction tx = new Transaction(userId, 1L, null, LocalDate.of(2026, 10, 1),
                "добавлено вручную", "Магазин", new BigDecimal("100.00"), "debit", null);
        when(transactionRepository.findByIdAndUserId(txId, userId)).thenReturn(Optional.of(tx));

        service.delete(userId, txId);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    void assignCategoryMarksManual() {
        Long userId = 1L, txId = 5L, categoryId = 7L;
        Transaction tx = new Transaction(userId, 1L, null, LocalDate.of(2026, 10, 1),
                "добавлено вручную", "Магазин", new BigDecimal("100.00"), "debit", null);
        when(transactionRepository.findByIdAndUserId(txId, userId)).thenReturn(Optional.of(tx));
        when(categoryRepository.findByIdAndUserId(categoryId, userId))
                .thenReturn(Optional.of(new Category(userId, "Аптека")));

        service.assignCategory(userId, txId, categoryId);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getCategoryId()).isEqualTo(categoryId);
        assertThat(captor.getValue().isCategoryManual()).isTrue();
    }

    @Test
    void reCategorizeAllSkipsManualCategory() {
        Long userId = 1L;
        Transaction manual = new Transaction(userId, 1L, null, LocalDate.of(2026, 10, 1),
                "добавлено вручную", "Магазин", new BigDecimal("100.00"), "debit", null);
        manual.setCategoryId(7L);
        manual.setCategoryManual(true);
        when(transactionRepository.findActiveByUserId(userId)).thenReturn(List.of(manual));

        int changed = service.reCategorizeAll(userId);

        assertThat(changed).isZero();
        assertThat(manual.getCategoryId()).isEqualTo(7L);
    }

    @Test
    void reCategorizeAllUpdatesNonManual() {
        Long userId = 1L;
        Transaction tx = new Transaction(userId, 1L, null, LocalDate.of(2026, 10, 1),
                "добавлено вручную", "Магазин", new BigDecimal("100.00"), "debit", null);
        Rule rule = new Rule(userId, null, "{\"purpose_contains\":\"магазин\"}", 7L, 0, false);
        when(ruleRepository.findByUserIdOrderByPriorityDescIdAsc(userId)).thenReturn(List.of(rule));
        when(transactionRepository.findActiveByUserId(userId)).thenReturn(List.of(tx));

        int changed = service.reCategorizeAll(userId);

        assertThat(changed).isEqualTo(1);
        assertThat(tx.getCategoryId()).isEqualTo(7L);
    }
}
