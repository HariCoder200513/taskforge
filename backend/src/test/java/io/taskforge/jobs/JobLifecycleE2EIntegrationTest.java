package io.taskforge.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import io.taskforge.jobs.worker.WorkerEngine;
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
 * Full lifecycle through the public API: an admin promotes a user to DEVELOPER
 * via the role endpoint, creates a queue and grants SUBMIT; the developer submits
 * a job and the worker engine runs it to SUCCEEDED. Ties auth + RBAC + role
 * management + the worker together. The scheduler is off in tests, so the engine
 * is driven explicitly. Requires Docker.
 */
@TestPropertySource(properties = "auth.jwt.access-token-ttl=30m")
class JobLifecycleE2EIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private WorkerEngine engine;

    private String adminToken;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from jobs");
        jdbc.update("delete from user_roles where role_id = (select id from roles where name = 'ADMIN')");
        register("e2e-admin");
        jdbc.update("insert into user_roles (user_id, role_id) "
                + "select u.id, r.id from users u, roles r where u.email = ? and r.name = 'ADMIN' "
                + "on conflict do nothing", email("e2e-admin"));
        adminToken = login("e2e-admin");
    }

    @Test
    void developerSubmitsAndWorkerRunsJobToSuccess() {
        // Admin promotes a fresh user to DEVELOPER through the role endpoint.
        String devId = register("e2e-dev");
        assertThat(post("/api/v1/users/" + devId + "/roles",
                Map.of("role", "DEVELOPER", "action", "GRANT"), adminToken).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        String devToken = login("e2e-dev");   // re-login so the token carries DEVELOPER

        // Admin creates a queue and grants the developer SUBMIT on it.
        String queueId = post("/api/v1/queues", Map.of("name", "e2e-" + System.nanoTime()), adminToken)
                .getBody().get("id").asText();
        assertThat(post("/api/v1/queues/" + queueId + "/permissions",
                Map.of("userId", devId, "permission", "SUBMIT"), adminToken).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        // Developer submits a job → PENDING.
        ResponseEntity<JsonNode> submit = post("/api/v1/jobs",
                Map.of("queueId", queueId, "payload", Map.of("echo", "e2e")), devToken);
        assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String jobId = submit.getBody().get("id").asText();
        assertThat(submit.getBody().get("status").asText()).isEqualTo("PENDING");

        // Worker claims and runs it.
        assertThat(engine.pollOnce()).isGreaterThanOrEqualTo(1);

        // Developer sees it finished.
        ResponseEntity<JsonNode> job = get("/api/v1/jobs/" + jobId, devToken);
        assertThat(job.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(job.getBody().get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(job.getBody().get("attempts").asInt()).isEqualTo(1);
    }

    // ---- helpers ------------------------------------------------------------

    private static String email(String local) {
        return local + "@example.com";
    }

    private String register(String local) {
        return post("/api/v1/auth/register",
                Map.of("email", email(local), "password", "sup3rsecretpwd", "name", local), null)
                .getBody().get("id").asText();
    }

    private String login(String local) {
        return post("/api/v1/auth/login",
                Map.of("email", email(local), "password", "sup3rsecretpwd"), null)
                .getBody().get("accessToken").asText();
    }

    private ResponseEntity<JsonNode> get(String path, String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) {
            h.setBearerAuth(token);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), JsonNode.class);
    }

    private ResponseEntity<JsonNode> post(String path, Object body, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            h.setBearerAuth(token);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class);
    }
}
