package io.github.smiskinext.meet.infrastructure.messaging;

import io.github.smiskinext.meet.domain.port.InviteTokenGenerator;
import io.github.smiskinext.meet.infrastructure.config.InviteProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Generates opaque, single-use invite tokens.
 *
 * <p>Each token is 256 bits of cryptographically secure randomness encoded as URL-safe Base64.
 * Only the SHA-256 hash of the raw token is persisted; the raw token is distributed via events and
 * never stored. Because the token carries no meeting or invitee data, two tokens issued in the same
 * instant remain distinct, so rotating an invite never collides on the token-hash uniqueness
 * constraint.
 */
@Component
public class OpaqueInviteTokenGenerator implements InviteTokenGenerator {

    private static final int TOKEN_BYTE_LENGTH = 32;

    private final SecureRandom random = new SecureRandom();
    private final Duration tokenExpiry;

    public OpaqueInviteTokenGenerator(InviteProperties properties) {
        this.tokenExpiry = Duration.ofDays(properties.tokenExpiryDays());
    }

    @Override
    public TokenResult generate() {
        Instant expiresAt = Instant.now().plus(tokenExpiry);

        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        random.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        String tokenHash = sha256Hex(rawToken);

        return new TokenResult(rawToken, tokenHash, expiresAt);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
