package ru.sviridov.wallet.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface StatementGapRepository extends JpaRepository<StatementGap, Long> {

    List<StatementGap> findByCardIdOrderByGapFromAsc(Long cardId);

    boolean existsByCardIdAndGapFromAndGapTo(Long cardId, LocalDate gapFrom, LocalDate gapTo);

    @Query("""
            select g from StatementGap g
            where g.cardId in (select c.id from Card c where c.userId = :userId)
            order by g.gapFrom asc
            """)
    List<StatementGap> findByUserIdOrderByGapFromAsc(@Param("userId") Long userId);
}
