package ru.sviridov.wallet.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.AdminRepository;
import ru.sviridov.wallet.domain.CardRepository;
import ru.sviridov.wallet.domain.CategoryRepository;
import ru.sviridov.wallet.domain.RuleRepository;
import ru.sviridov.wallet.domain.Statement;
import ru.sviridov.wallet.domain.StatementRepository;
import ru.sviridov.wallet.domain.TransactionRepository;
import ru.sviridov.wallet.domain.UserRepository;
import ru.sviridov.wallet.web.dto.ServerStatusResponse;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.time.Instant;

/**
 * Lightweight health snapshot for the admin "Сервер" page: no host CPU/RAM
 * metrics, just what a single-VPS operator cares about — version, uptime, DB
 * reachability, row counts, last import, JVM heap and disk on the working dir.
 */
@Service
public class ServerStatusService {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final TransactionRepository transactionRepository;
    private final StatementRepository statementRepository;
    private final CardRepository cardRepository;
    private final CategoryRepository categoryRepository;
    private final RuleRepository ruleRepository;
    private final JdbcTemplate jdbcTemplate;
    private final String version;

    public ServerStatusService(UserRepository userRepository,
                               AdminRepository adminRepository,
                               TransactionRepository transactionRepository,
                               StatementRepository statementRepository,
                               CardRepository cardRepository,
                               CategoryRepository categoryRepository,
                               RuleRepository ruleRepository,
                               JdbcTemplate jdbcTemplate,
                               @Value("${info.app.version:0.1.0}") String version) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.transactionRepository = transactionRepository;
        this.statementRepository = statementRepository;
        this.cardRepository = cardRepository;
        this.categoryRepository = categoryRepository;
        this.ruleRepository = ruleRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.version = version;
    }

    @Transactional(readOnly = true)
    public ServerStatusResponse status() {
        Runtime runtime = Runtime.getRuntime();
        File cwd = new File(".");
        return new ServerStatusResponse(
                version,
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                dbStatus(),
                userRepository.count(),
                adminRepository.count(),
                transactionRepository.count(),
                statementRepository.count(),
                cardRepository.count(),
                categoryRepository.count(),
                ruleRepository.count(),
                lastImportAt(),
                runtime.maxMemory(),
                runtime.totalMemory() - runtime.freeMemory(),
                cwd.getTotalSpace(),
                cwd.getUsableSpace());
    }

    private String dbStatus() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "ok";
        } catch (RuntimeException e) {
            return "error";
        }
    }

    private Instant lastImportAt() {
        return statementRepository.findTopByOrderByUploadedAtDesc()
                .map(Statement::getUploadedAt)
                .orElse(null);
    }
}
