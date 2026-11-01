package ru.sviridov.wallet.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Returns fingerprints already present for the given candidates, including
     * soft-deleted rows — a manually deleted transaction must not be restored on
     * re-upload (tombstone), so its fingerprint still blocks the duplicate.
     */
    @Query("select t.fingerprint from Transaction t where t.fingerprint in :fps")
    Set<String> findExistingFingerprints(@Param("fps") Collection<String> fps);

    /** Active (non soft-deleted) transactions, newest first. */
    @Query("""
            select t from Transaction t
            where t.userId = :userId and t.deletedAt is null
            order by t.date desc, t.id desc
            """)
    List<Transaction> findActiveByUserId(@Param("userId") Long userId);

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    /** Active (non soft-deleted) transactions imported from one statement. */
    @Query("""
            select t from Transaction t
            where t.statementId = :statementId and t.deletedAt is null
            """)
    List<Transaction> findActiveByStatementId(@Param("statementId") Long statementId);

    /**
     * All transactions imported from one statement, including soft-deleted
     * tombstones. Used to hard-delete a statement's data so a re-upload imports
     * it fresh (a tombstone fingerprint must not block it).
     */
    @Query("select t from Transaction t where t.statementId = :statementId")
    List<Transaction> findByStatementId(@Param("statementId") Long statementId);
}
