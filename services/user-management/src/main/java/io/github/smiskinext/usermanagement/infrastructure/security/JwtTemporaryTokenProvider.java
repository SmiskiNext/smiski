package io.github.smiskinext.usermanagement.infrastructure.security;

import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.domain.model.valueobject.TemporaryToken;
import io.github.smiskinext.usermanagement.domain.model.valueobject.TemporaryTokenPurpose;
import io.github.smiskinext.usermanagement.domain.port.TemporaryTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT-based implementation of TemporaryTokenProvider.
 * Generates short-lived tokens (5 minutes) for password reset flow.
 */
@Component
public class JwtTemporaryTokenProvider implements TemporaryTokenProvider {

    private static final Duration TOKEN_VALIDITY = Duration.ofMinutes(5);
    private static final String CLAIM_PURPOSE = "purpose";

    private final SecretKey secretKey;

    public JwtTemporaryTokenProvider(@Value("${app.jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public TemporaryToken generateToken(UserId userId, TemporaryTokenPurpose purpose) {
        Instant now = Instant.now();
        Instant expiry = now.plus(TOKEN_VALIDITY);

        String tokenValue = Jwts.builder()
                .header()
                .keyId("zms-temp-token")
                .and()
                .subject(userId.value().toString())
                .claim(CLAIM_PURPOSE, purpose.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey, Jwts.SIG.HS512)
                .compact();

        return TemporaryToken.of(tokenValue, userId, purpose, expiry);
    }

    @Override
    public TemporaryToken validateToken(String token, TemporaryTokenPurpose expectedPurpose) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            UserId userId = UserId.of(UUID.fromString(claims.getSubject()));
            String purposeStr = claims.get(CLAIM_PURPOSE, String.class);
            TemporaryTokenPurpose purpose = TemporaryTokenPurpose.valueOf(purposeStr);

            if (purpose != expectedPurpose) {
                throw new IllegalArgumentException("Token purpose mismatch");
            }

            Instant expiry = claims.getExpiration().toInstant();

            return new TemporaryToken(token, userId, purpose, expiry);

        } catch (JwtException e) {
            throw new IllegalArgumentException("Invalid temporary token", e);
        }
    }
}
