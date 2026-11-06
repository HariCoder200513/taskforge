package io.taskforge.auth.cookie;

import io.taskforge.config.AuthProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the refresh-token cookie: Secure, HttpOnly, SameSite=Strict, host-only
 * (no Domain), scoped to the auth path so it is only sent to /refresh and /logout.
 */
@Component
public class RefreshCookieFactory {

    private final AuthProperties props;

    public RefreshCookieFactory(AuthProperties props) {
        this.props = props;
    }

    public ResponseCookie create(String rawToken) {
        AuthProperties.Refresh r = props.refresh();
        return base(rawToken)
                .maxAge(r.slidingTtl())
                .build();
    }

    /** A Max-Age=0 cookie that clears the refresh cookie on logout. */
    public ResponseCookie clear() {
        return base("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        AuthProperties.Refresh r = props.refresh();
        return ResponseCookie.from(r.cookieName(), value)
                .httpOnly(true)
                .secure(r.cookieSecure())
                .sameSite("Strict")
                .path(r.cookiePath());
    }

    public String cookieName() {
        return props.refresh().cookieName();
    }
}
