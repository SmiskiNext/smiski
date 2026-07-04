package io.github.smiskinext.chatmanagement.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Validates JWT tokens and extracts the userId (subject claim) for chat endpoints.
 *
 * <p>Uses the same secret as meeting-management (shared via environment variable).
 * The injected secret must be at least 32 bytes (UTF-8), otherwise the bean fails fast
 * with {@link IllegalStateException} at startup.
 */
@Component
public class JwtValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final SecretKey secretKey;

    public JwtValidator(@Value("${app.chat.jwt-secret}") String secret) {
        byte[] secretBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (secret == null || secret.isBlank() || secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "CHAT_JWT_SECRET must be set to at least 32 characters. Configure it in services/docker/.env or run `pnpm smiski setup --env-only`.");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
    }

    /**
     * Validates a JWT token and returns the user ID (subject claim).
     *
     * @param token the raw JWT string
     * @return the userId, or {@code null} if the token is invalid
     */
    public String extractUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Returns {@code true} if the given token is valid (not expired, properly signed).
     */
    public boolean isValid(String token) {
        return extractUserId(token) != null;
    }
}
