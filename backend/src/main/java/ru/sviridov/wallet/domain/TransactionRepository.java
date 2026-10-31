package ru.sviridov.wallet.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
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
}
