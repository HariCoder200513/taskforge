package io.taskforge.auth.error;

import java.util.UUID;

/** Auth-domain exceptions, mapped to HTTP status by the global handler. */
public final class AuthExceptions {

    private AuthExceptions() {
    }

    /** Registration with an email that already exists → 409. */
    public static class EmailAlreadyExistsException extends RuntimeException {
        public EmailAlreadyExistsException() {
            super("Email is already registered");
        }
    }

    /** Wrong email/password, or disabled account → 401 (generic, never reveals which). */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Email or password is incorrect");
        }
    }

    /** Missing/expired/unknown refresh token → 401. */
    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException() {
            super("Refresh token is missing or invalid");
        }
    }

    /**
     * Reuse of an already-rotated token outside the grace window. The whole
     * family has been revoked by the time this is thrown. Maps to 401.
     */
    public static class ReplayDetectedException extends RuntimeException {
        private final UUID userId;
        private final UUID familyId;

        public ReplayDetectedException(UUID userId, UUID familyId) {
            super("Refresh token replay detected");
            this.userId = userId;
            this.familyId = familyId;
        }

        public UUID getUserId() {
            return userId;
        }

        public UUID getFamilyId() {
            return familyId;
        }
    }
}
