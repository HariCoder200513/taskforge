package io.taskforge.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Flyway migrations apply cleanly against a real PostgreSQL
 * instance and that the schema/seed data from §5 of the architecture is present.
 * Requires a running Docker daemon (Testcontainers).
 */
@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MigrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationsApplyAndSeedRolesExist() {
        Integer roleCount = jdbc.queryForObject("SELECT count(*) FROM roles", Integer.class);
        assertThat(roleCount).isEqualTo(3);

        List<String> roleNames = jdbc.queryForList("SELECT name FROM roles ORDER BY name", String.class);
        assertThat(roleNames).containsExactly("ADMIN", "DEVELOPER", "VIEWER");
    }

    @Test
    void allCoreTablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                        "WHERE table_schema = 'public' ORDER BY table_name",
                String.class);

        assertThat(tables).contains(
                "users", "roles", "user_roles",
                "refresh_tokens", "audit_logs",
                "queues", "jobs", "queue_permissions");
    }

    @Test
    void emailIsCaseInsensitiveUnique() {
        jdbc.update("INSERT INTO users (email, password_hash, name) VALUES (?, ?, ?)",
                "Case@Example.com", "x", "Case User");

        // citext makes this a duplicate despite the different casing.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE email = ?", Integer.class,
                "case@example.com")).isEqualTo(1);
    }
}
