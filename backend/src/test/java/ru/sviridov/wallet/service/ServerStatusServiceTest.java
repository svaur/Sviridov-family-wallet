package ru.sviridov.wallet.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.sviridov.wallet.domain.AdminRepository;
import ru.sviridov.wallet.domain.CardRepository;
import ru.sviridov.wallet.domain.CategoryRepository;
import ru.sviridov.wallet.domain.RuleRepository;
import ru.sviridov.wallet.domain.StatementRepository;
import ru.sviridov.wallet.domain.TransactionRepository;
import ru.sviridov.wallet.domain.UserRepository;
import ru.sviridov.wallet.web.dto.ServerStatusResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServerStatusServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminRepository adminRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private StatementRepository statementRepository;
    @Mock
    private CardRepository cardRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private RuleRepository ruleRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private ServerStatusService service() {
        return new ServerStatusService(userRepository, adminRepository, transactionRepository,
                statementRepository, cardRepository, categoryRepository, ruleRepository,
                jdbcTemplate, "0.1.0");
    }

    @Test
    void statusReportsCountsAndDbOk() {
        when(userRepository.count()).thenReturn(3L);
        when(adminRepository.count()).thenReturn(1L);
        when(transactionRepository.count()).thenReturn(120L);
        when(statementRepository.count()).thenReturn(4L);
        when(cardRepository.count()).thenReturn(2L);
        when(categoryRepository.count()).thenReturn(5L);
        when(ruleRepository.count()).thenReturn(6L);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(statementRepository.findTopByOrderByUploadedAtDesc()).thenReturn(Optional.empty());

        ServerStatusResponse status = service().status();

        assertThat(status.version()).isEqualTo("0.1.0");
        assertThat(status.dbStatus()).isEqualTo("ok");
        assertThat(status.userCount()).isEqualTo(3L);
        assertThat(status.adminCount()).isEqualTo(1L);
        assertThat(status.transactionCount()).isEqualTo(120L);
        assertThat(status.statementCount()).isEqualTo(4L);
        assertThat(status.cardCount()).isEqualTo(2L);
        assertThat(status.categoryCount()).isEqualTo(5L);
        assertThat(status.ruleCount()).isEqualTo(6L);
        assertThat(status.lastImportAt()).isNull();
    }

    @Test
    void statusReportsDbErrorWhenSelectFails() {
        when(statementRepository.findTopByOrderByUploadedAtDesc()).thenReturn(Optional.empty());
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new RuntimeException("boom"));

        ServerStatusResponse status = service().status();

        assertThat(status.dbStatus()).isEqualTo("error");
    }
}
