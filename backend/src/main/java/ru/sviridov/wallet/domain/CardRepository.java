package ru.sviridov.wallet.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {

    List<Card> findByUserIdOrderByIdAsc(Long userId);

    Optional<Card> findByIdAndUserId(Long id, Long userId);
}
