package io.taskforge.support;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for integration tests: boots the full app with a random web port against
 * a real PostgreSQL container (Flyway runs the migrations). Requires Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void authProps(DynamicPropertyRegistry registry) {
        // Short-lived access tokens and no Secure flag so the refresh cookie
        // round-trips over the test's plain HTTP client.
        registry.add("auth.refresh.cookie-secure", () -> "false");
        registry.add("auth.jwt.access-token-ttl", () -> "2s");
        registry.add("auth.refresh.grace-window", () -> "2s");
    }
}
