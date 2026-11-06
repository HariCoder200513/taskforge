package io.taskforge.auth;

import com.fasterxml.jackson.databind.JsonNode;
import io.taskforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of the authentication flows against a real app + Postgres:
 * registration, login, /me, refresh rotation, the multi-tab grace window, replay
 * detection (family revocation), CSRF rejection, and token expiry. Requires Docker.
 */
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private static final String CSRF_HEADER = "X-CSRF-Token";

    // ---- Registration -------------------------------------------------------

    @Test
    void register_createsViewer() {
        ResponseEntity<JsonNode> res = register("viewer@example.com", "sup3rsecretpw!", "Vee");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().get("roles").toString()).contains("VIEWER");
        assertThat(res.getBody().get("roles").toString()).doesNotContain("ADMIN");
    }

    @Test
    void register_duplicateEmail_conflict() {
        register("dup@example.com", "sup3rsecretpw!", "Dup");
        ResponseEntity<JsonNode> res = register("DUP@example.com", "sup3rsecretpw!", "Dup2");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().get("error").get("code").asText()).isEqualTo("EMAIL_TAKEN");
    }

    @Test
    void register_weakPassword_unprocessable() {
        ResponseEntity<JsonNode> res = register("weak@example.com", "short", "Weak");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().get("error").get("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    // ---- Login + /me --------------------------------------------------------

    @Test
    void login_thenMe_succeeds() {
        register("alice@example.com", "sup3rsecretpw!", "Alice");
        ResponseEntity<JsonNode> login = login("alice@example.com", "sup3rsecretpw!");
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        String access = login.getBody().get("accessToken").asText();
        assertThat(access).isNotBlank();
        assertThat(refreshCookie(login)).isNotBlank();

        ResponseEntity<JsonNode> me = me(access);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("email").asText()).isEqualTo("alice@example.com");
    }

    @Test
    void login_wrongPassword_unauthorizedGeneric() {
        register("bob@example.com", "sup3rsecretpw!", "Bob");
        ResponseEntity<JsonNode> res = login("bob@example.com", "wrongpassword!");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody().get("error").get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void me_withoutToken_unauthorized() {
        ResponseEntity<JsonNode> res = rest.exchange(
                "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- Refresh rotation ---------------------------------------------------

    @Test
    void refresh_rotatesAndReturnsNewAccessToken() {
        String cookie = loginGetCookie("carol@example.com");
        ResponseEntity<JsonNode> refreshed = refresh(cookie);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshed.getBody().get("accessToken").asText()).isNotBlank();
        assertThat(refreshCookie(refreshed)).isNotBlank().isNotEqualTo(cookie);
    }

    @Test
    void refresh_withoutCsrfHeader_forbidden() {
        String cookie = loginGetCookie("dave@example.com");
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "tf_refresh=" + cookie);   // no CSRF header
        ResponseEntity<JsonNode> res = rest.exchange(
                "/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(headers), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody().get("error").get("code").asText()).isEqualTo("CSRF_FAILED");
    }

    @Test
    void refresh_multiTabWithinGrace_bothSucceed() {
        String cookie = loginGetCookie("erin@example.com");
        ResponseEntity<JsonNode> first = refresh(cookie);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Immediately reuse the SAME old cookie (second tab) — within grace window.
        ResponseEntity<JsonNode> second = refresh(cookie);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void refresh_replayOutsideGrace_revokesFamily() throws InterruptedException {
        String cookie = loginGetCookie("frank@example.com");
        ResponseEntity<JsonNode> rotated = refresh(cookie);
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newCookie = refreshCookie(rotated);

        // Wait well past the grace window, then replay the old (used) cookie.
        Thread.sleep(2500);
        ResponseEntity<JsonNode> replay = refresh(cookie);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(replay.getBody().get("error").get("code").asText()).isEqualTo("INVALID_REFRESH_TOKEN");

        // Family was revoked, so even the legitimately-rotated cookie is dead now.
        ResponseEntity<JsonNode> afterRevoke = refresh(newCookie);
        assertThat(afterRevoke.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- Logout + expiry ----------------------------------------------------

    @Test
    void logout_revokesRefreshToken() {
        String cookie = loginGetCookie("gina@example.com");
        ResponseEntity<JsonNode> out = logout(cookie);
        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<JsonNode> afterLogout = refresh(cookie);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredAccessToken_rejected() throws InterruptedException {
        register("hank@example.com", "sup3rsecretpw!", "Hank");
        String access = login("hank@example.com", "sup3rsecretpw!").getBody().get("accessToken").asText();
        Thread.sleep(2500);   // access TTL is 2s in tests
        ResponseEntity<JsonNode> res = me(access);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- helpers ------------------------------------------------------------

    private ResponseEntity<JsonNode> register(String email, String password, String name) {
        return postJson("/api/v1/auth/register",
                Map.of("email", email, "password", password, "name", name), new HttpHeaders());
    }

    private ResponseEntity<JsonNode> login(String email, String password) {
        return postJson("/api/v1/auth/login",
                Map.of("email", email, "password", password), new HttpHeaders());
    }

    private ResponseEntity<JsonNode> me(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return rest.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> refresh(String cookieValue) {
        HttpHeaders headers = csrfHeaders(cookieValue);
        return rest.exchange("/api/v1/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> logout(String cookieValue) {
        HttpHeaders headers = csrfHeaders(cookieValue);
        return rest.exchange("/api/v1/auth/logout", HttpMethod.POST,
                new HttpEntity<>(headers), JsonNode.class);
    }

    private String loginGetCookie(String email) {
        register(email, "sup3rsecretpw!", "User");
        return refreshCookie(login(email, "sup3rsecretpw!"));
    }

    private HttpHeaders csrfHeaders(String cookieValue) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "tf_refresh=" + cookieValue);
        headers.add(CSRF_HEADER, "1");
        return headers;
    }

    private ResponseEntity<JsonNode> postJson(String path, Map<String, ?> body, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private String refreshCookie(ResponseEntity<?> response) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) {
            return null;
        }
        for (String c : setCookies) {
            if (c.startsWith("tf_refresh=")) {
                String value = c.substring("tf_refresh=".length());
                int semi = value.indexOf(';');
                return semi >= 0 ? value.substring(0, semi) : value;
            }
        }
        return null;
    }
}
