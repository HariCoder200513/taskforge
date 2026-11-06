package io.taskforge.auth.refresh;

import io.taskforge.auth.error.AuthExceptions.InvalidRefreshTokenException;
import io.taskforge.auth.error.AuthExceptions.ReplayDetectedException;
import io.taskforge.config.AuthProperties;
import io.taskforge.domain.RefreshToken;
import io.taskforge.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque refresh tokens with rotation, replay detection (family revocation) and
 * a multi-tab grace window. The raw token is returned to the caller (to set in
 * the cookie) but only its SHA-256 hash is persisted.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32; // 256-bit

    private final RefreshTokenRepository repository;
    private final AuthProperties props;

    public RefreshTokenService(RefreshTokenRepository repository, AuthProperties props) {
        this.repository = repository;
        this.props = props;
    }

    /** The raw token value to hand to the client, plus the stored row. */
    public record Issued(String rawToken, RefreshToken entity) {
    }

    /** Result of a successful rotation: who it belongs to and the new raw token. */
    public record Rotated(UUID userId, String rawToken) {
    }

    /** Start a brand-new session (fresh family + absolute cap). */
    @Transactional
    public Issued startSession(UUID userId, String userAgent, String ip) {
        Instant now = Instant.now();
        Instant absoluteCap = now.plus(props.refresh().absoluteTtl());
        return mint(userId, UUID.randomUUID(), absoluteCap, userAgent, ip, now);
    }

    /**
     * Rotate a presented refresh token. Handles: unknown/expired/revoked (401),
     * multi-tab grace reuse (allowed), and replay outside the grace window
     * (family revoked, {@link ReplayDetectedException}).
     */
    @Transactional
    public Rotated rotate(String rawToken, String userAgent, String ip) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        Instant now = Instant.now();
        String hash = sha256Hex(rawToken);

        RefreshToken current = repository.findByTokenHash(hash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (current.isRevoked() || current.isExpiredAt(now)) {
            throw new InvalidRefreshTokenException();
        }

        if (current.isUsed()) {
            Instant graceEnd = current.getUsedAt().plus(props.refresh().graceWindow());
            if (!now.isAfter(graceEnd)) {
                // Multi-tab race: a near-simultaneous second refresh. Not an
                // attack — mint a sibling in the same family (carry the cap).
                Issued sibling = mint(current.getUserId(), current.getFamilyId(),
                        current.getAbsoluteExpiresAt(), userAgent, ip, now);
                return new Rotated(current.getUserId(), sibling.rawToken());
            }
            // Reuse outside the grace window → replay. Revoke the whole family.
            repository.revokeFamily(current.getFamilyId(), now);
            throw new ReplayDetectedException(current.getUserId(), current.getFamilyId());
        }

        // Normal rotation.
        Issued next = mint(current.getUserId(), current.getFamilyId(),
                current.getAbsoluteExpiresAt(), userAgent, ip, now);
        current.markUsed(now, next.entity().getId());
        repository.save(current);
        return new Rotated(current.getUserId(), next.rawToken());
    }

    /** Revoke the presented token (logout). Idempotent. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(sha256Hex(rawToken))
                .ifPresent(t -> {
                    t.revoke(Instant.now());
                    repository.save(t);
                });
    }

    private Issued mint(UUID userId, UUID familyId, Instant absoluteCap,
                        String userAgent, String ip, Instant now) {
        Instant sliding = now.plus(props.refresh().slidingTtl());
        Instant expiresAt = sliding.isBefore(absoluteCap) ? sliding : absoluteCap;

        String raw = randomToken();
        RefreshToken entity = new RefreshToken(
                userId, sha256Hex(raw), familyId, expiresAt, absoluteCap, userAgent, ip);
        repository.save(entity);
        return new Issued(raw, entity);
    }

    private static String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
