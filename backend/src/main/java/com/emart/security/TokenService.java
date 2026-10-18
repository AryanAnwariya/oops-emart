package com.emart.security;

import com.emart.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Issues short-lived signed access tokens. There are no refresh tokens: users sign in again after expiry. */
@Service
public class TokenService {

    public record IssuedToken(String token, long expiresInSeconds) {}

    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public TokenService(JwtEncoder encoder,
                        @Value("${app.jwt.issuer:emart}") String issuer,
                        @Value("${app.jwt.ttl:PT30M}") Duration ttl) {
        this(encoder, issuer, ttl, Clock.systemUTC());
    }

    /** Package-private so tests can issue tokens at a chosen point in time. */
    TokenService(JwtEncoder encoder, String issuer, Duration ttl, Clock clock) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.ttl = ttl;
        this.clock = clock;
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim("role", user.getRole())
                .claim("email", user.getEmail())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, ttl.getSeconds());
    }
}
