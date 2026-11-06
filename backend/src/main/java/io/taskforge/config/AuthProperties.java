package io.taskforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * All authentication/authorization tunables. Values come from application.yml
 * under the {@code auth.*} prefix. Decisions recorded in ARCHITECTURE.md §9.2.
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        Jwt jwt,
        Refresh refresh,
        Csrf csrf,
        Cors cors,
        Proxy proxy
) {

    /** Access token (JWT) settings. */
    public record Jwt(
            String issuer,
            String audience,
            Duration accessTokenTtl,
            Duration clockSkew,
            String activeKid,
            String publicKeyPem,
            String privateKeyPem
    ) {}

    /** Opaque refresh token + cookie settings. */
    public record Refresh(
            Duration slidingTtl,
            Duration absoluteTtl,
            Duration graceWindow,
            String cookieName,
            String cookiePath,
            boolean cookieSecure
    ) {}

    /** CSRF defense for cookie endpoints (/refresh, /logout). */
    public record Csrf(
            String headerName,
            List<String> allowedOrigins
    ) {}

    /** CORS — disabled in prod (same-origin), enabled for local dev only. */
    public record Cors(
            boolean enabled,
            List<String> allowedOrigins
    ) {}

    /** Trusted-proxy handling for the forwarded client IP. */
    public record Proxy(
            String secret,
            String clientIpHeader
    ) {}
}
