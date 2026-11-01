package ru.sviridov.wallet.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.sviridov.wallet.domain.Admin;
import ru.sviridov.wallet.domain.AdminRepository;
import ru.sviridov.wallet.domain.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin-side user management over the real API: create/list/delete, ban/unban,
 * password reset and forced logout. Each test signs in as a fresh admin first.
 *
 * <p>The admin panel sees a global namespace (not per-user), so every test
 * starts from a clean slate — {@code deleteAll()} in {@code @BeforeEach}.</p>
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String ADMIN_PASSWORD = "admin-secret-1";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminUsername;

    @BeforeEach
    void createAdmin() {
        userRepository.deleteAll();
        adminRepository.deleteAll();
        adminUsername = "admin-" + UUID.randomUUID().toString().substring(0, 8);
        adminRepository.save(new Admin(adminUsername, passwordEncoder.encode(ADMIN_PASSWORD), false));
    }

    private Api admin() throws Exception {
        Api client = Api.anonymous(mvc, json);
        assertThat(client.loginAt("/api/admin/auth/login", adminUsername, ADMIN_PASSWORD)
                .getResponse().getStatus()).isEqualTo(200);
        return client;
    }

    private long createUser(Api admin, String username, String password) throws Exception {
        JsonNode created = admin.post("/api/admin/users",
                Map.of("username", username, "password", password));
        return created.get("id").asLong();
    }

    @Test
    void listCreateAndDelete() throws Exception {
        Api admin = admin();
        assertThat(admin.get("/api/admin/users")).isEmpty();

        JsonNode created = admin.post("/api/admin/users", Map.of(
                "username", "alice",
                "password", "alice-secret-1",
                "firstName", " Алиса ",
                "lastName", "Иванова"));
        assertThat(created.get("username").asText()).isEqualTo("alice");
        assertThat(created.get("firstName").asText()).isEqualTo("Алиса");
        assertThat(created.get("mustChangePassword").asBoolean()).isTrue();
        assertThat(created.get("active").asBoolean()).isTrue();

        assertThat(admin.get("/api/admin/users")).hasSize(1);

        var duplicate = admin.postRaw("/api/admin/users",
                Map.of("username", "alice", "password", "alice-secret-1"));
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(400);
        assertThat(json.readTree(duplicate.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("message").asText()).isEqualTo("Пользователь с таким логином уже существует");

        assertThat(admin.deleteRaw("/api/admin/users/" + created.get("id").asLong())
                .getResponse().getStatus()).isEqualTo(204);
        assertThat(admin.get("/api/admin/users")).isEmpty();
    }

    @Test
    void banBlocksLoginAndUnbanRestoresIt() throws Exception {
        Api admin = admin();
        long id = createUser(admin, "bob", "bob-secret-1");

        assertThat(admin.postRaw("/api/admin/users/" + id + "/ban", Map.of())
                .getResponse().getStatus()).isEqualTo(204);

        var bannedLogin = Api.anonymous(mvc, json).login("bob", "bob-secret-1");
        assertThat(bannedLogin.getResponse().getStatus()).isEqualTo(401);
        assertThat(json.readTree(bannedLogin.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("message").asText()).isEqualTo("Аккаунт заблокирован");

        assertThat(admin.postRaw("/api/admin/users/" + id + "/unban", Map.of())
                .getResponse().getStatus()).isEqualTo(204);
        assertThat(Api.anonymous(mvc, json).login("bob", "bob-secret-1")
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void resetPasswordReturnsAWorkingTempPassword() throws Exception {
        Api admin = admin();
        long id = createUser(admin, "carol", "carol-secret-1");

        JsonNode reset = admin.post("/api/admin/users/" + id + "/reset-password", Map.of());
        String temp = reset.get("tempPassword").asText();
        assertThat(temp).hasSize(12);

        assertThat(Api.anonymous(mvc, json).login("carol", temp).getResponse().getStatus()).isEqualTo(200);
        assertThat(Api.anonymous(mvc, json).login("carol", "carol-secret-1")
                .getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void forceLogoutKillsTheExistingSession() throws Exception {
        Api admin = admin();
        long id = createUser(admin, "dave", "dave-secret-1");

        Api wallet = Api.anonymous(mvc, json);
        assertThat(wallet.login("dave", "dave-secret-1").getResponse().getStatus()).isEqualTo(200);
        assertThat(wallet.get("/api/me").get("username").asText()).isEqualTo("dave");

        assertThat(admin.postRaw("/api/admin/users/" + id + "/logout", Map.of())
                .getResponse().getStatus()).isEqualTo(204);

        assertThat(wallet.getRaw("/api/me").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void auditRecordsAdminActionsAndStatusReportsHealth() throws Exception {
        Api admin = admin();
        createUser(admin, "eve", "eve-secret-1");

        JsonNode audit = admin.get("/api/admin/audit");
        assertThat(audit.get("totalElements").asLong()).isGreaterThanOrEqualTo(1);
        JsonNode entry = audit.get("content").get(0);
        assertThat(entry.get("entity").asText()).isEqualTo("user");
        assertThat(entry.get("action").asText()).isEqualTo("create");
        assertThat(entry.get("username").asText()).isEqualTo("eve");
        assertThat(entry.get("actorKind").asText()).isEqualTo("admin");

        JsonNode status = admin.get("/api/admin/status");
        assertThat(status.get("dbStatus").asText()).isEqualTo("ok");
        assertThat(status.get("version").asText()).isEqualTo("0.1.0");
        assertThat(status.get("userCount").asLong()).isEqualTo(1);
        assertThat(status.get("adminCount").asLong()).isEqualTo(1);
        assertThat(status.get("transactionCount").asLong()).isEqualTo(0);
    }
}
