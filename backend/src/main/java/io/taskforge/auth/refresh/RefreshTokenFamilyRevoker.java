package io.taskforge.auth.refresh;

import io.taskforge.repository.RefreshTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Revokes a whole token family in its OWN transaction. Replay detection throws
 * right after revoking, which would roll the revocation back if it ran in the
 * caller's transaction — REQUIRES_NEW commits it independently so the family
 * stays revoked even though the rotate() call ultimately fails.
 */
@Component
public class RefreshTokenFamilyRevoker {

    private final RefreshTokenRepository repository;

    public RefreshTokenFamilyRevoker(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(UUID familyId, Instant when) {
        repository.revokeFamily(familyId, when);
    }
}
