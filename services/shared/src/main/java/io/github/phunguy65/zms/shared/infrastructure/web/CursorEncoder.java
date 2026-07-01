package io.github.phunguy65.zms.shared.infrastructure.web;

import io.github.phunguy65.zms.shared.domain.CursorErrorCode;
import io.github.phunguy65.zms.shared.domain.CursorTokenEncoder;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.ScrollCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 implementation of {@link CursorTokenEncoder} for keyset pagination.
 *
 * <p>Token format (before Base64url encoding):
 *
 * <pre>
 * payload   = "&lt;createdAt_epoch_ms&gt;:&lt;uuid&gt;"
 * signature = hex(HMAC-SHA256(payload, secret)[0..15])  (first 16 bytes = 128-bit prefix)
 * raw       = payload + ":" + signature
 * token     = Base64url(raw)
 * </pre>
 *
 * <p>The HMAC signature prevents clients from crafting arbitrary cursors that could expose internal
 * DB values or skip rows. The secret is read from {@code app.cursor.secret} config property.
 *
 * <p>If the secret is rotated, all in-flight tokens become invalid. Clients should treat an
 * {@link CursorErrorCode#INVALID_CURSOR} result as "start from the beginning" and omit {@code pageToken}.
 */
@Component
public class CursorEncoder implements CursorTokenEncoder {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int HMAC_BYTES = 16;

    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();

    private final byte[] secretBytes;

    public CursorEncoder(@Value("${app.cursor.secret}") String secret) {
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Encodes a keyset cursor position into a signed, opaque token.
     *
     * @param createdAt the {@code created_at} timestamp of the last row on the current page
     * @param id        the {@code id} (UUID) of the last row on the current page
     * @return Base64url-encoded signed token
     */
    public String encode(Instant createdAt, UUID id) {
        String payload = createdAt.toEpochMilli() + ":" + id;
        String signature = hmacPrefix(payload);
        String raw = payload + ":" + signature;
        return BASE64_ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes and verifies a cursor token.
     *
     * @param token the Base64url-encoded token from a previous response's {@code nextPageToken}
     * @return {@link Result.Success} with a {@link ScrollCursor}, or
     *         {@link Result.Failure} with {@link CursorErrorCode#INVALID_CURSOR} if the token
     *         is malformed or the HMAC signature is invalid
     */
    public Result<ScrollCursor, CursorErrorCode> decode(String token) {
        String raw;
        try {
            raw = new String(BASE64_DECODER.decode(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Result.failure(CursorErrorCode.INVALID_CURSOR);
        }

        int lastColon = raw.lastIndexOf(':');
        if (lastColon < 0) {
            return Result.failure(CursorErrorCode.INVALID_CURSOR);
        }
        String payload = raw.substring(0, lastColon);
        String providedSig = raw.substring(lastColon + 1);

        String expectedSig = hmacPrefix(payload);
        if (!constantTimeEquals(expectedSig, providedSig)) {
            return Result.failure(CursorErrorCode.INVALID_CURSOR);
        }

        int colonIdx = payload.indexOf(':');
        if (colonIdx < 0) {
            return Result.failure(CursorErrorCode.INVALID_CURSOR);
        }
        try {
            long epochMs = Long.parseLong(payload.substring(0, colonIdx));
            UUID id = UUID.fromString(payload.substring(colonIdx + 1));
            return Result.success(new ScrollCursor(Instant.ofEpochMilli(epochMs), id));
        } catch (IllegalArgumentException e) {
            return Result.failure(CursorErrorCode.INVALID_CURSOR);
        }
    }

    /** Computes the first {@value HMAC_BYTES} bytes of HMAC-SHA256(payload, secret) as hex. */
    private String hmacPrefix(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(HMAC_BYTES * 2);
            for (int i = 0; i < HMAC_BYTES; i++) {
                sb.append(String.format("%02x", hmacBytes[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }

    /** Constant-time string comparison to prevent timing attacks. */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
