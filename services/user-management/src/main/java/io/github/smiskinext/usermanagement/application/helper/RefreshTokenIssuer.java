package io.github.smiskinext.usermanagement.application.helper;

import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.domain.model.RefreshToken;
import io.github.smiskinext.usermanagement.domain.port.RefreshTokenRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Service;

/**
 * Application service responsible for generating and persisting refresh tokens.
 * Centralises the token-generation logic that was previously duplicated across
 * LoginUserUseCase, LoginWithGoogleUseCase, and RefreshTokenUseCase.
 */
@Service
public class RefreshTokenIssuer {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenIssuer(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Generates a new refresh token for the given user, persists it, and returns the raw
     * (unhashed) token string that should be sent to the client.
     */
    public String issueAndSave(UserId userId, long expirySeconds) {
        byte[] rawBytes = new byte[32];
        secureRandom.nextBytes(rawBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes);

        String tokenHash = hash(rawToken);
        Instant expiresAt = Instant.now().plusSeconds(expirySeconds);
        refreshTokenRepository.save(RefreshToken.issue(userId, tokenHash, expiresAt));

        return rawToken;
    }

    /**
     * Returns the SHA-256 hex digest of the given token string.
     * Used to look up or validate a token received from the client.
     */
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes =
                    digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
