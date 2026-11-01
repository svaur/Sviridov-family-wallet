package ru.sviridov.wallet.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Tiny HTTP client for the integration tests: performs MockMvc calls, keeps the
 * session cookie after a successful login and parses JSON responses.
 *
 * <p>Kept deliberately dumb — the tests should read like the client flow
 * (login → call endpoint → assert JSON), not like MockMvc plumbing.</p>
 */
final class Api {

    private final MockMvc mvc;
    private final ObjectMapper json;
    private MockHttpSession session;

    Api(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    /** A client without a session — for the "not logged in" assertions. */
    static Api anonymous(MockMvc mvc, ObjectMapper json) {
        return new Api(mvc, json);
    }

    MockHttpSession session() {
        return session;
    }

    /** Reuse an existing session — for tests that log into both SPAs in one cookie. */
    void useSession(MockHttpSession shared) {
        this.session = shared;
    }

    MvcResult login(String username, String password) throws Exception {
        return loginAt("/api/auth/login", username, password);
    }

    /** Generic login; the admin panel signs in at a different path. */
    MvcResult loginAt(String path, String username, String password) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password)));
        // Attach an existing session so the second login lands in the same cookie.
        if (session != null) {
            builder.session(session);
        }
        MvcResult result = mvc.perform(builder).andReturn();
        if (result.getResponse().getStatus() == 200) {
            session = (MockHttpSession) result.getRequest().getSession(false);
        }
        return result;
    }

    MvcResult loginOk(String username, String password) throws Exception {
        MvcResult result = login(username, password);
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError("login failed: " + result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        }
        return result;
    }

    MvcResult getRaw(String path) throws Exception {
        return send(MockMvcRequestBuilders.get(path));
    }

    MvcResult postRaw(String path, Object body) throws Exception {
        return send(MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    MvcResult putRaw(String path, Object body) throws Exception {
        return send(MockMvcRequestBuilders.put(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    MvcResult deleteRaw(String path) throws Exception {
        return send(MockMvcRequestBuilders.delete(path));
    }

    MvcResult upload(String path, byte[] bytes, String filename) throws Exception {
        return send(MockMvcRequestBuilders.multipart(path)
                .file(new MockMultipartFile("file", filename, "application/pdf", bytes)));
    }

    JsonNode get(String path) throws Exception {
        return body(getRaw(path));
    }

    JsonNode post(String path, Object body) throws Exception {
        return body(postRaw(path, body));
    }

    JsonNode put(String path, Object body) throws Exception {
        return body(putRaw(path, body));
    }

    JsonNode delete(String path) throws Exception {
        return body(deleteRaw(path));
    }

    JsonNode body(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (content == null || content.isBlank()) {
            return json.nullNode();
        }
        return json.readTree(content);
    }

    private MvcResult send(MockHttpServletRequestBuilder builder) throws Exception {
        if (session != null) {
            builder.session(session);
        }
        return mvc.perform(builder).andReturn();
    }
}
