package com.emart.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Signing configuration. The secret only ever comes from the JWT_SECRET environment variable
 * (app.jwt.secret); the application refuses to start without a strong one.
 */
@Configuration
public class JwtConfig {

    static final int MIN_SECRET_BYTES = 32;

    @Bean
    public SecretKey jwtSecretKey(@Value("${app.jwt.secret:}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set to a random value of at least " + MIN_SECRET_BYTES + " bytes");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, @Value("${app.jwt.issuer:emart}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Default validators check expiry / not-before; the issuer validator checks iss.
        OAuth2TokenValidator<Jwt> validator =
                new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer));
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
