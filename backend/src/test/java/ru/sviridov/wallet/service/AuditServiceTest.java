package ru.sviridov.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import ru.sviridov.wallet.domain.AuditLog;
import ru.sviridov.wallet.domain.AuditLogRepository;
import ru.sviridov.wallet.web.dto.AuditResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository repository;

    @Captor
    private ArgumentCaptor<AuditLog> captor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuditService service() {
        return new AuditService(repository, objectMapper);
    }

    @Test
    void recordStoresUsernameInNewValue() {
        service().record("user", 7L, "ban", "ivan", 1L, "127.0.0.1");

        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getUserId()).isNull();
        assertThat(saved.getEntity()).isEqualTo("user");
        assertThat(saved.getEntityId()).isEqualTo(7L);
        assertThat(saved.getAction()).isEqualTo("ban");
        assertThat(saved.getActorKind()).isEqualTo("admin");
        assertThat(saved.getActorId()).isEqualTo(1L);
        assertThat(saved.getIp()).isEqualTo("127.0.0.1");
        assertThat(saved.getNewValue()).isEqualTo("{\"username\":\"ivan\"}");
    }

    @Test
    void listMapsAndExtractsUsername() {
        AuditLog log = new AuditLog(null, "user", 7L, "create", null,
                "{\"username\":\"ivan\"}", "admin", 1L, "127.0.0.1");
        when(repository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(log)));

        Page<AuditResponse> page = service().list(0, 50);

        assertThat(page.getTotalElements()).isEqualTo(1);
        AuditResponse response = page.getContent().get(0);
        assertThat(response.username()).isEqualTo("ivan");
        assertThat(response.action()).isEqualTo("create");
        assertThat(response.entityId()).isEqualTo(7L);
        assertThat(response.actorKind()).isEqualTo("admin");
        assertThat(response.actorId()).isEqualTo(1L);
        assertThat(response.ip()).isEqualTo("127.0.0.1");
    }

    @Test
    void listToleratesUnparsableNewValue() {
        AuditLog log = new AuditLog(null, "user", 7L, "create", null, "not-json", "admin", 1L, null);
        when(repository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(log)));

        AuditResponse response = service().list(0, 50).getContent().get(0);

        assertThat(response.username()).isNull();
    }
}
