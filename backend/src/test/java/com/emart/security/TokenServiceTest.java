package com.emart.security;

import com.emart.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {

    static final String SECRET = "unit-test-secret-unit-test-secret-0123";
    static final String ISSUER = "emart-test";
    final JwtConfig config = new JwtConfig();
    final SecretKey key = config.jwtSecretKey(SECRET);
    final JwtDecoder decoder = config.jwtDecoder(key, ISSUER);

    User user() {
        User u = new User("a@b.com", "hash", "RETAILER", null);
        u.setId(7);
        return u;
    }

    TokenService service(SecretKey k, String issuer, Duration ttl) {
        return new TokenService(config.jwtEncoder(k), issuer, ttl);
    }

    @Test
    void issuedToken_carriesExpectedClaimsAndShortLifetime() {
        TokenService.IssuedToken t = service(key, ISSUER, Duration.ofMinutes(30)).issue(user());
        Jwt jwt = decoder.decode(t.token());
        assertEquals("7", jwt.getSubject());
        assertEquals("RETAILER", jwt.getClaimAsString("role"));
        assertEquals("a@b.com", jwt.getClaimAsString("email"));
        assertEquals(ISSUER, jwt.getClaimAsString("iss"));
        assertNotNull(jwt.getId());
        assertEquals(1800, t.expiresInSeconds());
        assertEquals(1800, Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds());
    }

    @Test
    void expiredToken_isRejected() {
        Clock past = Clock.offset(Clock.systemUTC(), Duration.ofHours(-1));
        String token = new TokenService(config.jwtEncoder(key), ISSUER, Duration.ofMinutes(30), past).issue(user()).token();
        assertThrows(JwtValidationException.class, () -> decoder.decode(token));
    }

    @Test
    void tokenSignedWithOtherKey_isRejected() {
        SecretKey other = config.jwtSecretKey("another-secret-another-secret-0123456");
        String token = service(other, ISSUER, Duration.ofMinutes(5)).issue(user()).token();
        assertThrows(BadJwtException.class, () -> decoder.decode(token));
    }

    @Test
    void tokenFromOtherIssuer_isRejected() {
        String token = service(key, "someone-else", Duration.ofMinutes(5)).issue(user()).token();
        assertThrows(JwtValidationException.class, () -> decoder.decode(token));
    }

    @Test
    void tamperedPayload_isRejected() {
        String[] p = service(key, ISSUER, Duration.ofMinutes(5)).issue(user()).token().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(p[1])).replace("RETAILER", "WHOLESALER");
        String forged = p[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes()) + "." + p[2];
        assertThrows(BadJwtException.class, () -> decoder.decode(forged));
    }

    @Test
    void unsignedAlgNoneToken_isRejected() {
        Base64.Encoder e = Base64.getUrlEncoder().withoutPadding();
        String header = e.encodeToString("{\"alg\":\"none\"}".getBytes());
        long exp = Instant.now().plusSeconds(600).getEpochSecond();
        String body = e.encodeToString(("{\"sub\":\"7\",\"role\":\"RETAILER\",\"iss\":\"" + ISSUER + "\",\"exp\":" + exp + "}").getBytes());
        assertThrows(BadJwtException.class, () -> decoder.decode(header + "." + body + "."));
    }

    @Test
    void missingOrShortSecret_failsFast() {
        assertThrows(IllegalStateException.class, () -> config.jwtSecretKey(""));
        assertThrows(IllegalStateException.class, () -> config.jwtSecretKey(null));
        assertThrows(IllegalStateException.class, () -> config.jwtSecretKey("too-short"));
    }
}
