package ru.sviridov.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.AuditLog;
import ru.sviridov.wallet.domain.AuditLogRepository;
import ru.sviridov.wallet.web.dto.AuditResponse;

import java.util.Map;

/**
 * Append-only audit trail of admin actions. Entries are never updated or
 * deleted; the admin panel only reads them newest-first.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Records one action. The actor is always an admin; the target is identified
     * by {@code entity}/{@code entityId} and its username is snapshotted into
     * {@code newValue} so it stays readable after the user is deleted.
     */
    @Transactional
    public void record(String entity, Long entityId, String action, String username, Long actorId, String ip) {
        repository.save(new AuditLog(null, entity, entityId, action, null, usernameJson(username),
                "admin", actorId, ip));
    }

    @Transactional(readOnly = true)
    public Page<AuditResponse> list(int page, int size) {
        return repository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "at")))
                .map(log -> new AuditResponse(
                        log.getId(), log.getAt(), log.getActorKind(), log.getActorId(),
                        log.getEntity(), log.getEntityId(), log.getAction(),
                        username(log.getNewValue()), log.getIp()));
    }

    private String usernameJson(String username) {
        if (username == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(Map.of("username", username));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit payload", e);
        }
    }

    private String username(String newValueJson) {
        if (newValueJson == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(newValueJson);
            return node.hasNonNull("username") ? node.get("username").asText() : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
