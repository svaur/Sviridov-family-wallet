package ru.sviridov.wallet.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface StatementRepository extends JpaRepository<Statement, Long> {

    List<Statement> findByCardIdOrderByIdDesc(Long cardId);

    @Query("""
            select count(s) > 0 from Statement s
            where s.cardId = :cardId
              and s.periodFrom is not null and s.periodTo is not null
              and s.periodFrom <= :to and s.periodTo >= :from
            """)
    boolean existsOverlapping(@Param("cardId") Long cardId,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to);
}
