package ru.sviridov.wallet.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {

    List<Card> findByUserIdOrderByIdAsc(Long userId);

    Optional<Card> findByIdAndUserId(Long id, Long userId);

    /**
     * Cards are matched by last4, which is not unique by itself — re-importing a
     * statement or creating a card before last4-matching existed can leave
     * several rows with the same last4. {@code findFirst…} forces a single row
     * (ordered by id) so a duplicate never surfaces as
     * {@code IncorrectResultSizeDataAccessException}; the earliest card wins.
     */
    Optional<Card> findFirstByUserIdAndLast4OrderByIdAsc(Long userId, String last4);
}
