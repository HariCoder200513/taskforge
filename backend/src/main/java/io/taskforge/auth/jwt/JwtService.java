package io.taskforge.auth.jwt;

import io.taskforge.config.AuthProperties;
import io.taskforge.domain.Role;
import io.taskforge.domain.User;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithms;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Issues short-lived RS256 access tokens. */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final JwtKeyConfig keyConfig;
    private final AuthProperties props;

    public JwtService(JwtEncoder encoder, JwtKeyConfig keyConfig, AuthProperties props) {
        this.encoder = encoder;
        this.keyConfig = keyConfig;
        this.props = props;
    }

    public record AccessToken(String value, long expiresInSeconds) {
    }

    public AccessToken issue(User user) {
        Instant now = Instant.now();
        long ttlSeconds = props.jwt().accessTokenTtl().getSeconds();
        Instant expiresAt = now.plusSeconds(ttlSeconds);

        List<String> roles = user.getRoles().stream().map(Role::getName).sorted().toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .audience(List.of(props.jwt().audience()))
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .claim("name", user.getName())
                .build();

        JwsHeader header = JwsHeader.with(() -> JwsAlgorithms.RS256)
                .keyId(keyConfig.activeKid())
                .build();

        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, ttlSeconds);
    }
}
