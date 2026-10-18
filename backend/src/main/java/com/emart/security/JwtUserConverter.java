package com.emart.security;

import com.emart.model.User;
import com.emart.repository.UserRepo;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Runs after the signature, expiry and issuer checks. Re-validates the claims against the database on every
 * protected request, so a deleted user or a changed role invalidates tokens immediately, and builds the
 * principal that controllers use. Authorities come from the database role, not from client-controlled data.
 */
@Component
public class JwtUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final Set<String> ROLES = Set.of("CUSTOMER", "RETAILER", "WHOLESALER");

    private final UserRepo userRepo;

    public JwtUserConverter(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Integer userId = parseId(jwt.getSubject());
        String tokenRole = jwt.getClaimAsString("role");
        if (userId == null || tokenRole == null || !ROLES.contains(tokenRole)) {
            throw new InvalidBearerTokenException("Token claims are invalid");
        }
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new InvalidBearerTokenException("Token subject no longer exists"));
        if (!tokenRole.equals(user.getRole())) {
            throw new InvalidBearerTokenException("Token role is out of date; please sign in again");
        }
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
        return new UsernamePasswordAuthenticationToken(principal, jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
    }

    private static Integer parseId(String subject) {
        try {
            return subject == null ? null : Integer.valueOf(subject);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
