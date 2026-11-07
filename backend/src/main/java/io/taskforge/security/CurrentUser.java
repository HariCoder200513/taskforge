package io.taskforge.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Convenience access to the authenticated principal (from the validated JWT). */
@Component
public class CurrentUser {

    public UUID id() {
        // JwtAuthenticationToken.getName() returns the token's subject = user id.
        return UUID.fromString(auth().getName());
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean hasRole(String role) {
        String authority = "ROLE_" + role;
        return auth().getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private Authentication auth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
