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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.sviridov.wallet.domain.Admin;
import ru.sviridov.wallet.domain.AdminRepository;
import ru.sviridov.wallet.domain.User;
import ru.sviridov.wallet.domain.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The admin panel is a separate security chain on the same server with its own
 * session attribute ({@code ADMIN_SECURITY_CONTEXT_KEY}), so an admin login must
 * not clobber a wallet login and vice versa. These tests pin that boundary.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String ADMIN_PASSWORD = "admin-secret-1";
    private static final String WALLET_PASSWORD = "it-secret-1";

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
    private String username;

    @BeforeEach
    void createPrincipals() {
        adminUsername = "admin-" + UUID.randomUUID().toString().substring(0, 8);
        username = "it-" + UUID.randomUUID().toString().substring(0, 8);
        adminRepository.save(new Admin(adminUsername, passwordEncoder.encode(ADMIN_PASSWORD), true));
        userRepository.save(new User(username, passwordEncoder.encode(WALLET_PASSWORD)));
    }

    @Test
    void adminLoginLogoutAndMe() throws Exception {
        Api admin = Api.anonymous(mvc, json);

        MvcResult unauthorized = admin.getRaw("/api/admin/auth/me");
        assertThat(unauthorized.getResponse().getStatus()).isEqualTo(401);
        assertThat(json.readTree(unauthorized.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("message").asText()).isEqualTo("Требуется вход администратора");

        MvcResult badPassword = admin.loginAt("/api/admin/auth/login", adminUsername, "definitely-wrong");
        assertThat(badPassword.getResponse().getStatus()).isEqualTo(401);

        MvcResult ok = admin.loginAt("/api/admin/auth/login", adminUsername, ADMIN_PASSWORD);
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        JsonNode me = json.readTree(ok.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(me.get("username").asText()).isEqualTo(adminUsername);
        assertThat(me.get("mustChangePassword").asBoolean()).isTrue();

        assertThat(admin.get("/api/admin/auth/me").get("username").asText()).isEqualTo(adminUsername);

        assertThat(admin.postRaw("/api/admin/auth/logout", Map.of()).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(admin.getRaw("/api/admin/auth/me").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void walletUserCannotReachAdminApi() throws Exception {
        Api wallet = Api.anonymous(mvc, json);
        wallet.loginOk(username, WALLET_PASSWORD);

        // The wallet context is stored under a different key, so the admin chain
        // still sees an anonymous request.
        assertThat(wallet.getRaw("/api/admin/auth/me").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void adminCannotReachWalletApi() throws Exception {
        Api admin = Api.anonymous(mvc, json);
        admin.loginAt("/api/admin/auth/login", adminUsername, ADMIN_PASSWORD);

        assertThat(admin.getRaw("/api/me").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void walletAndAdminSessionsCoexistInOneCookie() throws Exception {
        Api wallet = Api.anonymous(mvc, json);
        wallet.loginOk(username, WALLET_PASSWORD);

        Api admin = Api.anonymous(mvc, json);
        admin.useSession(wallet.session());
        MvcResult adminLogin = admin.loginAt("/api/admin/auth/login", adminUsername, ADMIN_PASSWORD);
        assertThat(adminLogin.getResponse().getStatus()).isEqualTo(200);

        assertThat(wallet.get("/api/me").get("username").asText()).isEqualTo(username);
        assertThat(admin.get("/api/admin/auth/me").get("username").asText()).isEqualTo(adminUsername);

        // Admin logout removes only the admin attribute; the wallet stays signed in.
        admin.postRaw("/api/admin/auth/logout", Map.of());
        assertThat(admin.getRaw("/api/admin/auth/me").getResponse().getStatus()).isEqualTo(401);
        assertThat(wallet.get("/api/me").get("username").asText()).isEqualTo(username);
    }
}
