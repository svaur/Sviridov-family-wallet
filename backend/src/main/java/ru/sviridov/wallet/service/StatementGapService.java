package ru.sviridov.wallet.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.Statement;
import ru.sviridov.wallet.domain.StatementGap;
import ru.sviridov.wallet.domain.StatementGapRepository;
import ru.sviridov.wallet.domain.StatementRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Detects and lists breaks between consecutive statements of a card. A gap is
 * recorded when the pause between two statements exceeds a configurable number
 * of days ({@code app.gap.days}, default 7).
 */
@Service
public class StatementGapService {

    private final StatementGapRepository statementGapRepository;
    private final StatementRepository statementRepository;

    @Value("${app.gap.days:7}")
    private int gapDays = 7;

    public StatementGapService(StatementGapRepository statementGapRepository,
                               StatementRepository statementRepository) {
        this.statementGapRepository = statementGapRepository;
        this.statementRepository = statementRepository;
    }

    @Transactional(readOnly = true)
    public List<StatementGap> list(Long userId) {
        return statementGapRepository.findByUserIdOrderByGapFromAsc(userId);
    }

    /** Scans a card's statements and records any newly found gaps. */
    @Transactional
    public void detectGaps(Long cardId) {
        List<Statement> statements = statementRepository.findByCardIdOrderByIdDesc(cardId).stream()
                .filter(s -> s.getPeriodFrom() != null && s.getPeriodTo() != null)
                .sorted(Comparator.comparing(Statement::getPeriodFrom))
                .toList();
        for (int i = 1; i < statements.size(); i++) {
            Statement prev = statements.get(i - 1);
            Statement next = statements.get(i);
            long between = ChronoUnit.DAYS.between(prev.getPeriodTo(), next.getPeriodFrom());
            if (between > gapDays) {
                LocalDate gapFrom = prev.getPeriodTo().plusDays(1);
                LocalDate gapTo = next.getPeriodFrom().minusDays(1);
                if (!statementGapRepository.existsByCardIdAndGapFromAndGapTo(cardId, gapFrom, gapTo)) {
                    statementGapRepository.save(new StatementGap(cardId, gapFrom, gapTo));
                }
            }
        }
    }
}
