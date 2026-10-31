package ru.sviridov.wallet.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RuleRepository extends JpaRepository<Rule, Long> {

    List<Rule> findByUserIdOrderByPriorityDescIdAsc(Long userId);

    Optional<Rule> findByIdAndUserId(Long id, Long userId);
}
