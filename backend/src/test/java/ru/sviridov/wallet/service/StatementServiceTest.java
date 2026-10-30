package ru.sviridov.wallet.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.sviridov.wallet.domain.Card;
import ru.sviridov.wallet.domain.CardRepository;
import ru.sviridov.wallet.domain.Statement;
import ru.sviridov.wallet.domain.StatementRepository;
import ru.sviridov.wallet.domain.TransactionRepository;
import ru.sviridov.wallet.parser.ParsedRow;
import ru.sviridov.wallet.parser.ParsedStatement;
import ru.sviridov.wallet.parser.StatementParser;
import ru.sviridov.wallet.web.CardMismatchException;
import ru.sviridov.wallet.web.dto.StatementImportResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    @Mock
    private CardRepository cardRepository;
    @Mock
    private StatementRepository statementRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private StatementParser parser;

    @InjectMocks
    private StatementService service;

    @BeforeEach
    void stubSave() {
        lenient().when(statementRepository.save(any(Statement.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ParsedStatement statementOf(ParsedRow... rows) {
        return new ParsedStatement(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                "2202XXXXXXXX1234", "1234567890", "40817810000000000000",
                rows.length, List.of(rows), List.of());
    }

    private static ParsedRow debit(int num, String date, String amount, String purpose) {
        return new ParsedRow(num, LocalDate.parse(date), "Покупка", new BigDecimal(amount), null, purpose);
    }

    private void stubCard(Long userId, Long cardId) {
        Card card = new Card(userId, "Main", "Bank", "1234", "RUB");
        when(cardRepository.findByIdAndUserId(cardId, userId)).thenReturn(Optional.of(card));
    }

    @Test
    void importsAndSkipsDuplicates() throws Exception {
        Long userId = 1L, cardId = 10L;
        stubCard(userId, cardId);
        ParsedStatement parsed = statementOf(
                debit(1, "2026-09-05", "100.00", "Магазин А"),
                debit(2, "2026-09-06", "200.00", "Магазин Б"));
        when(parser.parse(any(byte[].class))).thenReturn(parsed);
        when(parser.version()).thenReturn("test-1.0");

        // first fingerprint already exists
        when(transactionRepository.findExistingFingerprints(anyCollection())).thenAnswer(inv -> {
            Collection<String> fps = inv.getArgument(0);
            return fps.stream().findFirst().map(Set::of).orElse(Set.of());
        });

        StatementImportResponse resp = service.importStatement(userId, cardId, new byte[]{1, 2});

        assertThat(resp.importedCount()).isEqualTo(1);
        assertThat(resp.skippedDuplicates()).isEqualTo(1);
        assertThat(resp.status()).isEqualTo("OK");
        assertThat(resp.rowCountRaw()).isEqualTo(2);
        verify(statementRepository).save(any(Statement.class));
        verify(transactionRepository).saveAll(anyList());
    }

    @Test
    void flagsRowWithBothColumnsAsUnrecognized() throws Exception {
        Long userId = 1L, cardId = 10L;
        stubCard(userId, cardId);
        ParsedRow bad = new ParsedRow(1, LocalDate.of(2026, 9, 5), "X",
                new BigDecimal("10.00"), new BigDecimal("20.00"), "p");
        ParsedStatement parsed = statementOf(bad);
        when(parser.parse(any(byte[].class))).thenReturn(parsed);

        StatementImportResponse resp = service.importStatement(userId, cardId, new byte[]{1});

        assertThat(resp.importedCount()).isEqualTo(0);
        assertThat(resp.status()).isEqualTo("PARTIAL");
        assertThat(resp.unrecognizedRows()).hasSize(1);
    }

    @Test
    void blocksImportOnCardMismatch() throws Exception {
        Long userId = 1L, cardId = 10L;
        Card card = new Card(userId, "Main", "Bank", "9999", "RUB"); // selected last4 = 9999
        when(cardRepository.findByIdAndUserId(cardId, userId)).thenReturn(Optional.of(card));
        ParsedStatement parsed = statementOf(debit(1, "2026-09-05", "100.00", "Магазин А"));
        // parsed.cardNumber = "2202XXXXXXXX1234" → extracted last4 = 1234 ≠ 9999
        when(parser.parse(any(byte[].class))).thenReturn(parsed);

        assertThatThrownBy(() -> service.importStatement(userId, cardId, new byte[]{1}))
                .isInstanceOf(CardMismatchException.class);
    }
}
