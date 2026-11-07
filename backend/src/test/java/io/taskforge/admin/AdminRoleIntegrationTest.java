package io.taskforge.admin;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RBAC for the admin role-management endpoint, plus the last-admin lockout guard.
 * The ADMIN population is reset per test so the guard is deterministic in the
 * shared test database. Access-token TTL is widened for these multi-step flows.
 * Requires Docker.
 */
@TestPropertySource(properties = "auth.jwt.access-token-ttl=30m")
class AdminRoleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;
    private String adminId;

    @BeforeEach
    void setUp() {
        // Isolate the admin set so countByRoleName('ADMIN') is deterministic here.
        jdbc.update("delete from user_roles where role_id = (select id from roles where name = 'ADMIN')");
        adminId = register("admin");
        makeAdmin("admin");
        adminToken = login("admin");
    }

    @Test
    void nonAdminCannotChangeRoles() {
        String viewerId = register("viewer");
        String viewerToken = login("viewer");
        assertThat(changeRole(viewerToken, viewerId, "DEVELOPER", "GRANT").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminGrantsAndRevokesDeveloper() {
        String devId = register("dev");

        ResponseEntity<JsonNode> granted = changeRole(adminToken, devId, "DEVELOPER", "GRANT");
        assertThat(granted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(roles(granted)).contains("DEVELOPER");

        ResponseEntity<JsonNode> revoked = changeRole(adminToken, devId, "DEVELOPER", "REVOKE");
        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(roles(revoked)).doesNotContain("DEVELOPER");
    }

    @Test
    void cannotRemoveLastAdmin() {
        // The acting admin is the only ADMIN → revoking it is refused (409).
        assertThat(changeRole(adminToken, adminId, "ADMIN", "REVOKE").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void canRemoveAdminWhenAnotherExists() {
        String secondId = register("admin2");
        assertThat(changeRole(adminToken, secondId, "ADMIN", "GRANT").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // Two admins now, so removing one is allowed.
        assertThat(changeRole(adminToken, secondId, "ADMIN", "REVOKE").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void unknownUserReturns404() {
        assertThat(changeRole(adminToken, "00000000-0000-0000-0000-000000000000",
                "DEVELOPER", "GRANT").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void invalidRoleReturns422() {
        String devId = register("dev");
        assertThat(changeRole(adminToken, devId, "SUPERUSER", "GRANT").getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ---- helpers ------------------------------------------------------------

    private static String email(String local) {
        return "adm-" + local + "@example.com";
    }

    private String register(String local) {
        return post("/api/v1/auth/register",
                Map.of("email", email(local), "password", "sup3rsecretpwd", "name", local), null)
                .getBody().get("id").asText();
    }

    private void makeAdmin(String local) {
        jdbc.update("insert into user_roles (user_id, role_id) "
                + "select u.id, r.id from users u, roles r where u.email = ? and r.name = 'ADMIN' "
                + "on conflict do nothing", email(local));
    }

    private String login(String local) {
        return post("/api/v1/auth/login",
                Map.of("email", email(local), "password", "sup3rsecretpwd"), null)
                .getBody().get("accessToken").asText();
    }

    private ResponseEntity<JsonNode> changeRole(String token, String userId, String role, String action) {
        return post("/api/v1/users/" + userId + "/roles",
                Map.of("role", role, "action", action), token);
    }

    private static List<String> roles(ResponseEntity<JsonNode> resp) {
        List<String> out = new ArrayList<>();
        resp.getBody().get("roles").forEach(n -> out.add(n.asText()));
        return out;
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
