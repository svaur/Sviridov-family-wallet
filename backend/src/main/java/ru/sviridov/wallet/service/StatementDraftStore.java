package ru.sviridov.wallet.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of upload drafts keyed by a random id. Drafts are transient —
 * they only need to survive the three HTTP calls of the upload wizard — so a
 * lost draft is harmless (the source PDF can simply be re-uploaded). Drafts
 * expire after {@value #TTL_MINUTES} minutes.
 */
@Component
public class StatementDraftStore {

    private static final long TTL_MINUTES = 30;

    private final ConcurrentHashMap<String, StatementDraft> drafts = new ConcurrentHashMap<>();

    public String put(StatementDraft draft) {
        String id = UUID.randomUUID().toString();
        drafts.put(id, draft);
        return id;
    }

    /** Replaces an existing draft in place (keeps its id). */
    public void replace(String id, StatementDraft draft) {
        drafts.put(id, draft);
    }

    public StatementDraft get(String id) {
        StatementDraft draft = drafts.get(id);
        if (draft == null) {
            return null;
        }
        if (draft.createdAt().plus(TTL_MINUTES, ChronoUnit.MINUTES).isBefore(Instant.now())) {
            drafts.remove(id);
            return null;
        }
        return draft;
    }

    public void remove(String id) {
        drafts.remove(id);
    }
}
