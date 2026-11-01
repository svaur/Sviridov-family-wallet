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
import ru.sviridov.wallet.domain.User;
import ru.sviridov.wallet.domain.UserRepository;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end client paths over a real PostgreSQL (Testcontainers), real Flyway
 * migrations, the real security filter chain and MockMvc instead of a network
 * port — fast, hermetic and still "the whole app".
 *
 * <p>Isolation: every test creates its own user. The application already
 * partitions all data by {@code user_id}, so tests cannot see each other's rows
 * and no truncation/cleanup is needed.</p>
 *
 * <p>Run with {@code mvn verify} (failsafe). {@code mvn test} stays Docker-free.</p>
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class WalletFlowIT {

    /** Same image as docker-compose, so migrations meet the production flavour. */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String PASSWORD = "it-secret-1";
    private static final String NEW_PASSWORD = "it-secret-2-changed";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String username;
    private Api api;

    @BeforeEach
    void createUser() throws Exception {
        username = "it-" + UUID.randomUUID().toString().substring(0, 8);
        userRepository.save(new User(username, passwordEncoder.encode(PASSWORD)));
        api = new Api(mvc, json);
        api.loginOk(username, PASSWORD);
    }

    // ---------------------------------------------------------------- paths

    @Test
    void loginLogoutAndAccessControl() throws Exception {
        Api anon = Api.anonymous(mvc, json);

        MvcResult unauthorized = anon.getRaw("/api/transactions");
        assertThat(unauthorized.getResponse().getStatus()).isEqualTo(401);
        JsonNode error = json.readTree(unauthorized.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(error.get("status").asInt()).isEqualTo(401);
        assertThat(error.get("message").asText()).isEqualTo("Требуется аутентификация");
        assertThat(error.get("path").asText()).isEqualTo("/api/transactions");

        MvcResult badPassword = anon.login(username, "definitely-wrong");
        assertThat(badPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(json.readTree(badPassword.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("message").asText()).isEqualTo("Неверный логин или пароль");

        MvcResult ok = anon.login(username, PASSWORD);
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(anon.get("/api/me").get("username").asText()).isEqualTo(username);
        assertThat(anon.get("/api/transactions").isArray()).isTrue();

        assertThat(anon.postRaw("/api/auth/logout", Map.of()).getResponse().getStatus()).isEqualTo(200);
        // Scoped logout: the wallet context is gone (next request is 401), but
        // the session itself survives so a coexisting admin session in the same
        // cookie lives on.
        assertThat(anon.getRaw("/api/transactions").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void profileAndPasswordChange() throws Exception {
        JsonNode profile = api.put("/api/me/profile", Map.of("firstName", " Иван ", "lastName", "Свиридов"));
        assertThat(profile.get("firstName").asText()).isEqualTo("Иван");
        assertThat(profile.get("lastName").asText()).isEqualTo("Свиридов");
        assertThat(api.get("/api/me").get("firstName").asText()).isEqualTo("Иван");

        MvcResult shortPassword = api.putRaw("/api/me/password",
                Map.of("currentPassword", PASSWORD, "newPassword", "short"));
        assertThat(shortPassword.getResponse().getStatus()).isEqualTo(400);

        MvcResult wrongCurrent = api.putRaw("/api/me/password",
                Map.of("currentPassword", "not-my-password", "newPassword", "brand-new-1"));
        assertThat(wrongCurrent.getResponse().getStatus()).isEqualTo(400);
        assertThat(json.readTree(wrongCurrent.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("message").asText()).isEqualTo("Неверный текущий пароль");

        MvcResult changed = api.putRaw("/api/me/password",
                Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD));
        assertThat(changed.getResponse().getStatus()).isEqualTo(204);

        // Every session is stale now, this one included (the epoch moved).
        assertThat(api.getRaw("/api/me").getResponse().getStatus()).isEqualTo(401);

        assertThat(Api.anonymous(mvc, json).login(username, PASSWORD).getResponse().getStatus())
                .isEqualTo(401);
        Api fresh = Api.anonymous(mvc, json);
        assertThat(fresh.login(username, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
        assertThat(fresh.get("/api/me").get("firstName").asText()).isEqualTo("Иван");
    }

    @Test
    void cardsCrud() throws Exception {
        JsonNode created = api.post("/api/cards",
                Map.of("cardNumber", "2202XXXXXXXX5555", "displayName", "Тестовая карта"));
        long cardId = created.get("id").asLong();
        assertThat(created.get("last4").asText()).isEqualTo("5555");
        assertThat(created.get("currency").asText()).isEqualTo("RUB");
        assertThat(api.get("/api/cards")).hasSize(1);

        JsonNode renamed = api.put("/api/cards/" + cardId, Map.of("displayName", "Зарплатная"));
        assertThat(renamed.get("displayName").asText()).isEqualTo("Зарплатная");
        assertThat(renamed.get("last4").asText()).isEqualTo("5555");

        MvcResult duplicate = api.postRaw("/api/cards",
                Map.of("cardNumber", "2202XXXXXXXX5555", "displayName", "Дубль"));
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(400);
        assertThat(json.readTree(duplicate.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("message").asText()).contains("уже существует");

        MvcResult invalid = api.postRaw("/api/cards",
                Map.of("cardNumber", "2202XXXXXXXX7777", "displayName", ""));
        assertThat(invalid.getResponse().getStatus()).isEqualTo(400);
        JsonNode validation = json.readTree(invalid.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(validation.get("fieldErrors").get(0).get("field").asText()).isEqualTo("displayName");

        assertThat(api.deleteRaw("/api/cards/" + cardId).getResponse().getStatus()).isEqualTo(204);
        assertThat(api.get("/api/cards")).isEmpty();
    }

    @Test
    void pdfImportWithRuleCategorization() throws Exception {
        JsonNode preview = previewPdf();
        assertThat(preview.get("periodFrom").asText()).isEqualTo("2026-09-01");
        assertThat(preview.get("periodTo").asText()).isEqualTo("2026-09-30");
        assertThat(preview.get("cardNumber").asText()).isEqualTo("2202XXXXXXXX1234");
        assertThat(preview.get("rowCountRaw").asInt()).isEqualTo(12);
        assertThat(preview.get("validCount").asInt()).isEqualTo(12);
        assertThat(preview.get("unrecognizedRows")).isEmpty();

        JsonNode category = api.post("/api/categories", Map.of("name", "Продукты", "icon", "🛒"));
        long categoryId = category.get("id").asLong();
        api.post("/api/rules", Map.of(
                "name", "Продукты",
                "categoryId", categoryId,
                "params", Map.of("operation_contains", "ПРОДУКТЫ")));

        JsonNode categorized = api.post("/api/statements/categorize", preview.get("transactions"));
        JsonNode groceries = findByOperation(categorized, "Покупка ПРОДУКТЫ ОНЛАЙН");
        assertThat(groceries.get("categoryId").asLong()).isEqualTo(categoryId);
        assertThat(groceries.get("categoryName").asText()).isEqualTo("Продукты");

        JsonNode imported = confirm(preview, categorized);
        assertThat(imported.get("importedCount").asInt()).isEqualTo(12);
        assertThat(imported.get("skippedDuplicates").asInt()).isZero();

        assertThat(api.get("/api/transactions")).hasSize(12);
        assertThat(api.get("/api/statements")).hasSize(1);

        JsonNode transactions = api.get("/api/transactions");
        JsonNode salary = findByOperation(transactions, "Зачисление зарплаты");
        assertThat(salary.get("type").asText()).isEqualTo("credit");
        assertThat(salary.get("amount").decimalValue()).isEqualByComparingTo("120000.00");
        assertThat(salary.get("source").asText()).isEqualTo("statement");
    }

    @Test
    void duplicatesAreSkippedAndDeletingAStatementRemovesItsData() throws Exception {
        JsonNode first = confirm(previewPdf(), null);
        assertThat(first.get("importedCount").asInt()).isEqualTo(12);

        JsonNode second = confirm(previewPdf(), null);
        assertThat(second.get("importedCount").asInt()).isZero();
        assertThat(second.get("skippedDuplicates").asInt()).isEqualTo(12);
        // A fully duplicate re-import is not a new statement.
        assertThat(second.get("statementId").isNull()).isTrue();
        assertThat(api.get("/api/transactions")).hasSize(12);
        assertThat(api.get("/api/statements")).hasSize(1);

        long statementId = api.get("/api/statements").get(0).get("id").asLong();
        assertThat(api.delete("/api/statements/" + statementId).asInt()).isEqualTo(12);
        assertThat(api.get("/api/transactions")).isEmpty();
        assertThat(api.get("/api/statements")).isEmpty();

        // Hard delete: the very same file can be imported again.
        JsonNode third = confirm(previewPdf(), null);
        assertThat(third.get("importedCount").asInt()).isEqualTo(12);
    }

    @Test
    void analyticsTotalsAndExclusions() throws Exception {
        confirm(previewPdf(), null);

        JsonNode totals = api.get("/api/analytics?from=2026-09-01&to=2026-09-30");
        assertThat(totals.get("income").decimalValue()).isEqualByComparingTo("135935.00");
        assertThat(totals.get("expense").decimalValue()).isEqualByComparingTo("23589.40");
        assertThat(totals.get("balance").decimalValue()).isEqualByComparingTo("112345.60");
        assertThat(totals.get("categories")).hasSize(1);
        assertThat(totals.get("categories").get(0).get("amount").decimalValue())
                .isEqualByComparingTo("23589.40");

        // exclude=0 is the sentinel for "без категории": nothing left to sum.
        JsonNode excluded = api.get("/api/analytics?from=2026-09-01&to=2026-09-30&exclude=0");
        assertThat(excluded.get("income").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(excluded.get("expense").decimalValue()).isEqualByComparingTo("0.00");

        // The system "исключено" category drops rows from analytics.
        long excludedCategoryId = excludedCategoryId();
        List<Long> transferIds = new ArrayList<>();
        for (JsonNode tx : api.get("/api/transactions")) {
            if ("Перевод средств".equals(tx.get("operation").asText())) {
                transferIds.add(tx.get("id").asLong());
            }
        }
        assertThat(transferIds).hasSize(3);
        for (Long id : transferIds) {
            api.post("/api/transactions/" + id + "/category", Map.of("categoryId", excludedCategoryId));
        }

        JsonNode afterExclusion = api.get("/api/analytics?from=2026-09-01&to=2026-09-30");
        assertThat(afterExclusion.get("expense").decimalValue()).isEqualByComparingTo("13219.40");
        assertThat(afterExclusion.get("income").decimalValue()).isEqualByComparingTo("135935.00");
    }

    @Test
    void manualTransactionLifecycle() throws Exception {
        JsonNode created = api.post("/api/transactions", Map.of(
                "source", "cash",
                "date", "2026-09-15",
                "type", "debit",
                "amount", 500.00,
                "purpose", "Кофе"));
        long id = created.get("id").asLong();
        assertThat(created.get("source").asText()).isEqualTo("cash");
        assertThat(created.get("operation").asText()).isEqualTo("добавлено вручную");

        JsonNode edited = api.put("/api/transactions/" + id, Map.of(
                "operation", "Кофе с собой",
                "amount", 450.00,
                "date", "2026-09-16"));
        assertThat(edited.get("operation").asText()).isEqualTo("Кофе с собой");
        assertThat(edited.get("amount").decimalValue()).isEqualByComparingTo("450.00");
        assertThat(edited.get("date").asText()).isEqualTo("2026-09-16");

        JsonNode commented = api.put("/api/transactions/" + id + "/comment",
                Map.of("comment", "проверено"));
        assertThat(commented.get("comment").asText()).isEqualTo("проверено");

        MvcResult invalid = api.putRaw("/api/transactions/" + id, Map.of(
                "operation", "Кофе", "amount", 0, "date", "2026-09-16"));
        assertThat(invalid.getResponse().getStatus()).isEqualTo(400);

        assertThat(api.deleteRaw("/api/transactions/" + id).getResponse().getStatus()).isEqualTo(204);
        assertThat(api.get("/api/transactions")).isEmpty();
    }

    @Test
    void forcedPasswordChangeAfterAdminReset() throws Exception {
        // An admin reset flips the flag; the wallet then blocks everything until
        // the new password is set.
        String tempUsername = "it-reset-" + UUID.randomUUID().toString().substring(0, 8);
        User reset = new User(tempUsername, passwordEncoder.encode("temp-password-1"));
        reset.setMustChangePassword(true);
        userRepository.save(reset);

        Api client = Api.anonymous(mvc, json);
        assertThat(client.login(tempUsername, "temp-password-1").getResponse().getStatus()).isEqualTo(200);

        MvcResult blocked = client.getRaw("/api/transactions");
        assertThat(blocked.getResponse().getStatus()).isEqualTo(403);
        assertThat(json.readTree(blocked.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("message").asText()).isEqualTo("Требуется смена пароля");

        assertThat(client.get("/api/me").get("mustChangePassword").asBoolean()).isTrue();

        assertThat(client.putRaw("/api/me/password",
                Map.of("currentPassword", "temp-password-1", "newPassword", "fresh-secret-1"))
                .getResponse().getStatus()).isEqualTo(204);

        // The change bumped the epoch: the current session is stale.
        assertThat(client.getRaw("/api/me").getResponse().getStatus()).isEqualTo(401);

        Api fresh = Api.anonymous(mvc, json);
        assertThat(fresh.login(tempUsername, "fresh-secret-1").getResponse().getStatus()).isEqualTo(200);
        assertThat(fresh.get("/api/me").get("mustChangePassword").asBoolean()).isFalse();
        assertThat(fresh.get("/api/transactions").isArray()).isTrue();
    }

    // -------------------------------------------------------------- helpers

    private JsonNode previewPdf() throws Exception {
        MvcResult result = api.upload("/api/statements/preview", testPdf(), "TEST_PDF.pdf");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return api.body(result);
    }

    /** Confirms the preview; rows are passed through unchanged when null. */
    private JsonNode confirm(JsonNode preview, JsonNode transactions) throws Exception {
        JsonNode rows = transactions == null ? preview.get("transactions") : transactions;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cardNumber", preview.get("cardNumber").asText());
        body.put("parserVersion", preview.get("parserVersion").asText());
        body.put("periodFrom", preview.get("periodFrom").asText());
        body.put("periodTo", preview.get("periodTo").asText());
        body.put("contract", textOrNull(preview, "contract"));
        body.put("account", textOrNull(preview, "account"));
        body.put("rowCountRaw", preview.get("rowCountRaw").asInt());
        body.put("warnings", json.convertValue(preview.get("warnings"), List.class));
        body.put("unrecognizedRows", json.convertValue(preview.get("unrecognizedRows"), List.class));
        body.put("transactions", json.convertValue(rows, List.class));

        MvcResult result = api.postRaw("/api/statements/confirm", body);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return api.body(result);
    }

    private long excludedCategoryId() throws Exception {
        for (JsonNode category : api.get("/api/categories")) {
            if (category.get("isExcluded").asBoolean()) {
                return category.get("id").asLong();
            }
        }
        throw new AssertionError("system category 'исключено' was not seeded");
    }

    private static JsonNode findByOperation(JsonNode transactions, String operation) {
        for (JsonNode tx : transactions) {
            if (operation.equals(tx.get("operation").asText())) {
                return tx;
            }
        }
        throw new AssertionError("transaction not found: " + operation);
    }

    private static Object textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static byte[] testPdf() throws Exception {
        try (InputStream in = WalletFlowIT.class.getResourceAsStream("/TEST_PDF.pdf")) {
            if (in == null) {
                throw new IllegalStateException("TEST_PDF.pdf is missing from the test resources");
            }
            return in.readAllBytes();
        }
    }
}
