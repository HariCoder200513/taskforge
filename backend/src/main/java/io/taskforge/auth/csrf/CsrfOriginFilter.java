package io.taskforge.auth.csrf;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskforge.config.AuthProperties;
import io.taskforge.web.ApiError;
import io.taskforge.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * CSRF defense for the cookie-authenticated endpoints (/refresh, /logout).
 * SameSite=Strict alone is not enough, so we additionally require:
 *   1. a custom request header (forces a CORS preflight; a simple cross-site
 *      form cannot set it), and
 *   2. an Origin header that, when present, is on the allowlist.
 */
public class CsrfOriginFilter extends OncePerRequestFilter {

    private final AuthProperties props;
    private final ObjectMapper objectMapper;

    public CsrfOriginFilter(AuthProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return !(uri.equals("/api/v1/auth/refresh") || uri.equals("/api/v1/auth/logout"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String headerName = props.csrf().headerName();
        if (request.getHeader(headerName) == null) {
            reject(request, response, "Missing CSRF header");
            return;
        }

        String origin = request.getHeader("Origin");
        if (origin != null) {
            List<String> allowed = props.csrf().allowedOrigins();
            if (allowed == null || !allowed.contains(origin)) {
                reject(request, response, "Origin not allowed");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = ApiError.of("CSRF_FAILED", message,
                requestId != null ? requestId.toString() : null);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
