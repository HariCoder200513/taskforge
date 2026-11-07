package io.taskforge.admin;

import io.taskforge.audit.AuditService;
import io.taskforge.auth.dto.AuthDtos.UserDto;
import io.taskforge.auth.error.AuthExceptions.ResourceNotFoundException;
import io.taskforge.domain.Role;
import io.taskforge.domain.RoleName;
import io.taskforge.domain.User;
import io.taskforge.repository.RoleRepository;
import io.taskforge.repository.UserRepository;
import io.taskforge.security.CurrentUser;
import io.taskforge.web.ConflictException;
import io.taskforge.web.RequestMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Admin-only role management plus the startup bootstrap. Grant/revoke are
 * idempotent; revoking ADMIN is refused when it would remove the last remaining
 * administrator, so the platform can never be locked out of its own admin API.
 * Every change is audited.
 */
@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public UserAdminService(UserRepository users, RoleRepository roles,
                            AuditService audit, CurrentUser currentUser) {
        this.users = users;
        this.roles = roles;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional
    public UserDto grantRole(UUID userId, RoleName role, RequestMeta meta) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (hasRole(user, role)) {
            return UserDto.from(user); // already has it — no-op
        }
        user.addRole(requireRole(role));
        users.save(user);
        audit.record("ROLE_GRANT", currentUser.id(), "user", userId.toString(),
                meta.ip(), meta.requestId(), Map.of("role", role.name()));
        return UserDto.from(user);
    }

    @Transactional
    public UserDto revokeRole(UUID userId, RoleName role, RequestMeta meta) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!hasRole(user, role)) {
            return UserDto.from(user); // doesn't have it — no-op
        }
        if (role == RoleName.ADMIN && users.countByRoleName(RoleName.ADMIN.name()) <= 1) {
            throw new ConflictException("Cannot remove the last remaining ADMIN");
        }
        user.getRoles().removeIf(r -> r.getName().equals(role.name()));
        users.save(user);
        audit.record("ROLE_REVOKE", currentUser.id(), "user", userId.toString(),
                meta.ip(), meta.requestId(), Map.of("role", role.name()));
        return UserDto.from(user);
    }

    /**
     * Startup bootstrap: promote a configured, already-registered user to ADMIN.
     * Idempotent — a no-op if they are already ADMIN; a warning if no such user
     * exists yet (register that email first, then restart).
     */
    @Transactional
    public void ensureAdminByEmail(String email) {
        users.findByEmail(email).ifPresentOrElse(user -> {
            if (hasRole(user, RoleName.ADMIN)) {
                log.info("Admin bootstrap: {} already has ADMIN", email);
            } else {
                user.addRole(requireRole(RoleName.ADMIN));
                users.save(user);
                audit.record("ROLE_BOOTSTRAP", null, "user", user.getId().toString(),
                        null, null, Map.of("role", "ADMIN", "email", email));
                log.info("Admin bootstrap: promoted {} to ADMIN", email);
            }
        }, () -> log.warn("Admin bootstrap: no user with email {} — register that user, then restart", email));
    }

    private Role requireRole(RoleName role) {
        return roles.findByName(role.name())
                .orElseThrow(() -> new IllegalStateException(role + " role not seeded"));
    }

    private static boolean hasRole(User user, RoleName role) {
        return user.getRoles().stream().anyMatch(r -> r.getName().equals(role.name()));
    }
}
