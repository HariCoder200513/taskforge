package io.taskforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bootstrap-admin config ({@code auth.bootstrap.*}). When {@code adminEmail} is
 * set, that already-registered user is promoted to ADMIN at startup (idempotent).
 * This solves the chicken-and-egg of getting the first admin into the system,
 * since ADMIN is never granted through self-registration.
 */
@ConfigurationProperties(prefix = "auth.bootstrap")
public record AdminBootstrapProperties(String adminEmail) {
}
