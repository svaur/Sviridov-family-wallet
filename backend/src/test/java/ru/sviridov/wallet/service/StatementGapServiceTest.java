package ru.sviridov.wallet.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.sviridov.wallet.domain.Statement;
import ru.sviridov.wallet.domain.StatementGap;
import ru.sviridov.wallet.domain.StatementGapRepository;
import ru.sviridov.wallet.domain.StatementRepository;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementGapServiceTest {

    @Mock
    private StatementGapRepository statementGapRepository;
    @Mock
    private StatementRepository statementRepository;

    private StatementGapService service;

    @BeforeEach
    void setUp() {
        service = new StatementGapService(statementGapRepository, statementRepository);
    }

    private static Statement statement(String from, String to) {
        return new Statement(1L, LocalDate.parse(from), LocalDate.parse(to), "v1", "OK", 1, 1,
                "2202XXXXXXXX1234", "contract", "account");
    }

    @Test
    void recordsGapWhenPauseExceedsThreshold() {
        when(statementRepository.findByCardIdOrderByIdDesc(1L)).thenReturn(List.of(
                statement("2026-09-01", "2026-09-30"),
                statement("2026-10-15", "2026-10-31")));
        when(statementGapRepository.existsByCardIdAndGapFromAndGapTo(
                1L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14))).thenReturn(false);

        service.detectGaps(1L);

        ArgumentCaptor<StatementGap> captor = ArgumentCaptor.forClass(StatementGap.class);
        verify(statementGapRepository).save(captor.capture());
        assertThat(captor.getValue().getGapFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(captor.getValue().getGapTo()).isEqualTo(LocalDate.of(2026, 10, 14));
    }

    @Test
    void noGapWhenPauseWithinThreshold() {
        when(statementRepository.findByCardIdOrderByIdDesc(1L)).thenReturn(List.of(
                statement("2026-09-01", "2026-09-30"),
                statement("2026-10-05", "2026-10-31")));

        service.detectGaps(1L);

        verify(statementGapRepository, never()).save(any(StatementGap.class));
    }
}
