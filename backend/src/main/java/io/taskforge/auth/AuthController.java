package io.taskforge.auth;

import io.taskforge.auth.cookie.RefreshCookieFactory;
import io.taskforge.auth.dto.AuthDtos.LoginRequest;
import io.taskforge.auth.dto.AuthDtos.LoginResponse;
import io.taskforge.auth.dto.AuthDtos.RegisterRequest;
import io.taskforge.auth.dto.AuthDtos.TokenResponse;
import io.taskforge.auth.dto.AuthDtos.UserDto;
import io.taskforge.web.RequestMeta;
import io.taskforge.web.RequestMetaResolver;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieFactory cookies;
    private final RequestMetaResolver metaResolver;

    public AuthController(AuthService authService, RefreshCookieFactory cookies,
                         RequestMetaResolver metaResolver) {
        this.authService = authService;
        this.cookies = cookies;
        this.metaResolver = metaResolver;
    }

    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest req,
                                            HttpServletRequest http) {
        UserDto user = authService.register(req, metaResolver.resolve(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                               HttpServletRequest http) {
        AuthService.LoginResult result = authService.login(req, metaResolver.resolve(http));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken()).toString())
                .body(result.body());
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest http) {
        RequestMeta meta = metaResolver.resolve(http);
        AuthService.RefreshResult result = authService.refresh(readRefreshCookie(http), meta);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken()).toString())
                .body(result.body());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        authService.logout(readRefreshCookie(http), metaResolver.resolve(http));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(authService.me(userId));
    }

    private String readRefreshCookie(HttpServletRequest http) {
        if (http.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : http.getCookies()) {
            if (cookie.getName().equals(cookies.cookieName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
