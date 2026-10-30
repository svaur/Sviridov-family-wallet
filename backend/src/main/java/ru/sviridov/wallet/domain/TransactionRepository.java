package ru.sviridov.wallet.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Returns fingerprints already present for the given candidates, including
     * soft-deleted rows — a manually deleted transaction must not be restored on
     * re-upload (tombstone), so its fingerprint still blocks the duplicate.
     */
    @Query("select t.fingerprint from Transaction t where t.fingerprint in :fps")
    Set<String> findExistingFingerprints(@Param("fps") Collection<String> fps);
}
