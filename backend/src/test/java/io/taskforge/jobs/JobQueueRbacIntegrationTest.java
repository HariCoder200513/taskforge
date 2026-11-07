package io.taskforge.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import io.taskforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RBAC matrix + IDOR coverage for the queue/job APIs. Roles beyond VIEWER are
 * assigned directly in the DB (no admin role endpoint yet). Requires Docker.
 *
 * Overrides the 2s access-token TTL from application-test.yml (that short TTL is
 * for the auth expiry test) — these multi-step tests need tokens that outlive a
 * sequence of calls.
 */
@TestPropertySource(properties = "auth.jwt.access-token-ttl=30m")
class JobQueueRbacIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;
    private String devToken;
    private String viewerToken;

    @BeforeEach
    void setUp() {
        adminToken = userWithRole("admin", "ADMIN");
        devToken = userWithRole("dev", "DEVELOPER");
        viewerToken = userWithRole("viewer", "VIEWER");
    }

    @Test
    void onlyAdminCanCreateQueue() {
        assertThat(createQueue(adminToken, uniqueName()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createQueue(devToken, uniqueName()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(createQueue(viewerToken, uniqueName()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void developerCanSubmitOnlyWithPermission() {
        String queueId = createQueue(adminToken, uniqueName()).getBody().get("id").asText();

        // No SUBMIT grant yet, and dev can't even see the queue → 404.
        assertThat(submit(devToken, queueId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        grant(adminToken, queueId, devUserId(), "SUBMIT");
        assertThat(submit(devToken, queueId).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void viewerCannotSubmit() {
        String queueId = createQueue(adminToken, uniqueName()).getBody().get("id").asText();
        assertThat(submit(viewerToken, queueId).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unseenJobReturns404_notForbidden() {
        String queueId = createQueue(adminToken, uniqueName()).getBody().get("id").asText();
        grant(adminToken, queueId, devUserId(), "SUBMIT");
        String jobId = submit(devToken, queueId).getBody().get("id").asText();

        // Another developer with no permission on the queue must not learn it exists.
        String otherDev = userWithRole("otherdev", "DEVELOPER");
        assertThat(getJob(otherDev, jobId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Owner can see it.
        assertThat(getJob(devToken, jobId).getStatusCode()).isEqualTo(HttpStatus.OK);
        // Admin can see it.
        assertThat(getJob(adminToken, jobId).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void retryRequiresRetryPermission() {
        String queueId = createQueue(adminToken, uniqueName()).getBody().get("id").asText();
        grant(adminToken, queueId, devUserId(), "SUBMIT");
        String jobId = submit(devToken, queueId).getBody().get("id").asText();

        // Dev owns the job but lacks RETRY → 403 (ownership alone doesn't grant retry).
        assertThat(retry(devToken, jobId).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        grant(adminToken, queueId, devUserId(), "RETRY");
        assertThat(retry(devToken, jobId).getStatusCode()).isEqualTo(HttpStatus.OK);
        // Admin can always retry.
        assertThat(retry(adminToken, jobId).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void unseenQueueReturns404() {
        String queueId = createQueue(adminToken, uniqueName()).getBody().get("id").asText();
        assertThat(getQueue(viewerToken, queueId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getQueue(adminToken, queueId).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---- helpers ------------------------------------------------------------

    private ResponseEntity<JsonNode> createQueue(String token, String name) {
        return post("/api/v1/queues", Map.of("name", name), token);
    }

    private ResponseEntity<JsonNode> getQueue(String token, String id) {
        return get("/api/v1/queues/" + id, token);
    }

    private void grant(String token, String queueId, String userId, String perm) {
        post("/api/v1/queues/" + queueId + "/permissions",
                Map.of("userId", userId, "permission", perm), token);
    }

    private ResponseEntity<JsonNode> submit(String token, String queueId) {
        return post("/api/v1/jobs", Map.of("queueId", queueId, "payload", Map.of("x", 1)), token);
    }

    private ResponseEntity<JsonNode> getJob(String token, String id) {
        return get("/api/v1/jobs/" + id, token);
    }

    private ResponseEntity<JsonNode> retry(String token, String id) {
        return post("/api/v1/jobs/" + id + "/retry", null, token);
    }

    // Unique email namespace so these users never collide with other test classes
    // sharing the same database.
    private static String email(String local) {
        return "rbac-" + local + "@example.com";
    }

    private String devUserId() {
        return jdbc.queryForObject("select id::text from users where email = ?", String.class, email("dev"));
    }

    /** Register a user, assign the role in the DB, and return a logged-in access token. */
    private String userWithRole(String local, String role) {
        String email = email(local);
        post("/api/v1/auth/register",
                Map.of("email", email, "password", "sup3rsecretpwd", "name", local), null);
        if (!role.equals("VIEWER")) {
            jdbc.update("insert into user_roles (user_id, role_id) "
                    + "select u.id, r.id from users u, roles r where u.email = ? and r.name = ? "
                    + "on conflict do nothing", email, role);
        }
        ResponseEntity<JsonNode> login = post("/api/v1/auth/login",
                Map.of("email", email, "password", "sup3rsecretpwd"), null);
        return login.getBody().get("accessToken").asText();
    }

    private static int counter = 0;

    private String uniqueName() {
        return "q-" + System.nanoTime() + "-" + (counter++);
    }

    private ResponseEntity<JsonNode> get(String path, String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), JsonNode.class);
    }

    private ResponseEntity<JsonNode> post(String path, Object body, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class);
    }
}
