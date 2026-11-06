package io.taskforge.auth;

import io.taskforge.audit.AuditService;
import io.taskforge.auth.dto.AuthDtos.LoginRequest;
import io.taskforge.auth.dto.AuthDtos.LoginResponse;
import io.taskforge.auth.dto.AuthDtos.RegisterRequest;
import io.taskforge.auth.dto.AuthDtos.TokenResponse;
import io.taskforge.auth.dto.AuthDtos.UserDto;
import io.taskforge.auth.error.AuthExceptions.EmailAlreadyExistsException;
import io.taskforge.auth.error.AuthExceptions.InvalidCredentialsException;
import io.taskforge.auth.error.AuthExceptions.InvalidRefreshTokenException;
import io.taskforge.auth.error.AuthExceptions.ReplayDetectedException;
import io.taskforge.auth.jwt.JwtService;
import io.taskforge.auth.refresh.RefreshTokenService;
import io.taskforge.domain.Role;
import io.taskforge.domain.RoleName;
import io.taskforge.domain.User;
import io.taskforge.repository.RoleRepository;
import io.taskforge.repository.UserRepository;
import io.taskforge.web.RequestMeta;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates registration, login, refresh (with rotation/replay handling),
 * logout and current-user lookup. Authorization decisions live elsewhere; this
 * class owns authentication and its audit trail.
 */
@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final AuditService audit;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokens, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
    }

    public record LoginResult(LoginResponse body, String refreshToken) {
    }

    public record RefreshResult(TokenResponse body, String refreshToken) {
    }

    /** Self-registration. Always creates a VIEWER. */
    @Transactional
    public UserDto register(RegisterRequest req, RequestMeta meta) {
        if (users.existsByEmail(req.email())) {
            throw new EmailAlreadyExistsException();
        }
        User user = new User(req.email(), passwordEncoder.encode(req.password()), req.name());
        Role viewer = roles.findByName(RoleName.VIEWER.name())
                .orElseThrow(() -> new IllegalStateException("VIEWER role not seeded"));
        user.addRole(viewer);
        users.save(user);

        audit.record("REGISTER", user.getId(), meta.ip(), meta.requestId());
        return UserDto.from(user);
    }

    /** Verify credentials, issue an access token and start a refresh session. */
    @Transactional
    public LoginResult login(LoginRequest req, RequestMeta meta) {
        User user = users.findByEmail(req.email()).orElse(null);

        // Verify password even when the user is unknown-ish to keep timing even
        // is out of scope here; we at least avoid leaking which failed.
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            audit.record("LOGIN_FAIL", user == null ? null : user.getId(), meta.ip(), meta.requestId());
            throw new InvalidCredentialsException();
        }
        if (!user.isActive()) {
            audit.record("LOGIN_FAIL", user.getId(), meta.ip(), meta.requestId());
            throw new InvalidCredentialsException();
        }

        JwtService.AccessToken access = jwtService.issue(user);
        RefreshTokenService.Issued refresh = refreshTokens.startSession(user.getId(), meta.userAgent(), meta.ip());

        audit.record("LOGIN_SUCCESS", user.getId(), meta.ip(), meta.requestId());

        LoginResponse body = new LoginResponse(
                access.value(), TOKEN_TYPE, access.expiresInSeconds(), UserDto.from(user));
        return new LoginResult(body, refresh.rawToken());
    }

    /** Rotate the refresh token and mint a new access token. */
    @Transactional
    public RefreshResult refresh(String rawRefreshToken, RequestMeta meta) {
        RefreshTokenService.Rotated rotated;
        try {
            rotated = refreshTokens.rotate(rawRefreshToken, meta.userAgent(), meta.ip());
        } catch (ReplayDetectedException replay) {
            audit.record("REPLAY_DETECTED", replay.getUserId(), null, null,
                    meta.ip(), meta.requestId(),
                    java.util.Map.of("familyId", replay.getFamilyId().toString()));
            throw new InvalidRefreshTokenException();
        }

        User user = users.findById(rotated.userId())
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!user.isActive()) {
            throw new InvalidRefreshTokenException();
        }

        JwtService.AccessToken access = jwtService.issue(user);
        audit.record("TOKEN_REFRESH", user.getId(), meta.ip(), meta.requestId());

        TokenResponse body = new TokenResponse(access.value(), TOKEN_TYPE, access.expiresInSeconds());
        return new RefreshResult(body, rotated.rawToken());
    }

    /** Revoke the presented refresh token. */
    @Transactional
    public void logout(String rawRefreshToken, RequestMeta meta) {
        refreshTokens.revoke(rawRefreshToken);
        audit.record("LOGOUT", null, meta.ip(), meta.requestId());
    }

    @Transactional(readOnly = true)
    public UserDto me(UUID userId) {
        User user = users.findById(userId).orElseThrow(InvalidCredentialsException::new);
        return UserDto.from(user);
    }
}
