package io.taskforge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskforge.auth.csrf.CsrfOriginFilter;
import io.taskforge.web.ApiError;
import io.taskforge.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity   // enables @PreAuthorize for resource-level RBAC (job APIs)
public class SecurityConfig {

    private final AuthProperties props;

    public SecurityConfig(AuthProperties props) {
        this.props = props;
    }

    /** Argon2id with ~64MB memory, 3 iterations, parallelism 1 (ARCHITECTURE §6). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // saltLength=16, hashLength=32, parallelism=1, memory=64MB (KiB), iterations=3
        return new Argon2PasswordEncoder(16, 32, 1, 1 << 16, 3);
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ObjectMapper objectMapper,
                                                   JwtAuthenticationConverter jwtAuthConverter)
            throws Exception {

        http
                // We implement CSRF for cookie endpoints ourselves (CsrfOriginFilter);
                // the rest of the API is stateless Bearer-token auth.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(accessDeniedHandler(objectMapper)))
                .addFilterBefore(new CsrfOriginFilter(props, objectMapper),
                        BearerTokenAuthenticationFilter.class);

        if (props.cors().enabled()) {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
        } else {
            http.cors(cors -> cors.disable());
        }

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);   // required for the refresh cookie in dev
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper mapper) {
        return (request, response, authException) ->
                writeError(mapper, request, response, HttpStatus.UNAUTHORIZED,
                        "UNAUTHENTICATED", "Authentication is required");
    }

    private AccessDeniedHandler accessDeniedHandler(ObjectMapper mapper) {
        return (request, response, deniedException) ->
                writeError(mapper, request, response, HttpStatus.FORBIDDEN,
                        "FORBIDDEN", "You do not have permission to perform this action");
    }

    private void writeError(ObjectMapper mapper, HttpServletRequest request,
                            jakarta.servlet.http.HttpServletResponse response,
                            HttpStatus status, String code, String message) throws java.io.IOException {
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getWriter(),
                ApiError.of(code, message, requestId != null ? requestId.toString() : null));
    }
}
