package io.taskforge.auth.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.taskforge.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * Builds the RS256 signing material and the Spring Security JWT encoder/decoder.
 * The signing algorithm is pinned to RS256; the decoder validates signature,
 * {@code exp}, {@code iss} and {@code aud}. The signing key carries a {@code kid}
 * so keys can be rotated later.
 */
@Configuration
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    private final RSAKey rsaKey;
    private final AuthProperties props;

    public JwtKeyConfig(AuthProperties props) {
        this.props = props;
        this.rsaKey = buildRsaKey(props.jwt());
    }

    private RSAKey buildRsaKey(AuthProperties.Jwt jwt) {
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;

        if (hasText(jwt.publicKeyPem()) && hasText(jwt.privateKeyPem())) {
            publicKey = parsePublicKey(jwt.publicKeyPem());
            privateKey = parsePrivateKey(jwt.privateKeyPem());
        } else {
            log.warn("No RSA keys configured (auth.jwt.*-key-pem); generating an "
                    + "ephemeral keypair. DEV ONLY — tokens will not survive a restart.");
            KeyPair pair = generateKeyPair();
            publicKey = (RSAPublicKey) pair.getPublic();
            privateKey = (RSAPrivateKey) pair.getPrivate();
        }

        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(jwt.activeKid())
                .build();
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder
                    .withPublicKey(rsaKey.toRSAPublicKey())
                    .signatureAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                    .build();
            decoder.setJwtValidator(tokenValidator());
            return decoder;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build JwtDecoder", e);
        }
    }

    private OAuth2TokenValidator<Jwt> tokenValidator() {
        OAuth2TokenValidator<Jwt> timestamp = new JwtTimestampValidator(props.jwt().clockSkew());
        OAuth2TokenValidator<Jwt> issuer = new JwtIssuerValidator(props.jwt().issuer());
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(props.jwt().audience()));
        return new DelegatingOAuth2TokenValidator<>(timestamp, issuer, audience);
    }

    /** The active signing kid, exposed for the encoder header. */
    public String activeKid() {
        return rsaKey.getKeyID();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate RSA keypair", e);
        }
    }

    private static RSAPublicKey parsePublicKey(String pem) {
        try {
            byte[] der = pemBody(pem, "PUBLIC KEY");
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid RSA public key PEM", e);
        }
    }

    private static RSAPrivateKey parsePrivateKey(String pem) {
        try {
            byte[] der = pemBody(pem, "PRIVATE KEY");
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid RSA private key PEM (expected PKCS#8)", e);
        }
    }

    private static byte[] pemBody(String pem, String type) {
        String normalized = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
