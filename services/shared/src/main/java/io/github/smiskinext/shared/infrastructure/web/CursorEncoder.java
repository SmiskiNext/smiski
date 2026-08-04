package io.github.smiskinext.shared.infrastructure.web;

import io.github.smiskinext.shared.domain.CursorErrorCode;
import io.github.smiskinext.shared.domain.CursorTokenEncoder;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.ScrollCursor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
 * signed    = "&lt;context_length&gt;:&lt;context&gt;:" + payload
 * signature = hex(HMAC-SHA256(signed, secret)[0..15])  (first 16 bytes = 128-bit prefix)
 * raw       = payload + ":" + signature
 * token     = Base64url(raw)
 * </pre>
 *
 * <p>The HMAC signature prevents clients from crafting arbitrary cursors that could expose internal
 * DB values or skip rows. The secret is read from {@code app.cursor.secret} config property.
 *
 * <p>The caller-supplied {@code context} is length-prefixed before signing so that distinct
 * {@code (context, payload)} pairs can never produce identical signed bytes, then bound into the
 * signature without being stored in the token. A token therefore only verifies under the exact
 * context it was issued for, and the token length is unaffected by the context.
 *
 * <p>Decoding requires the token to be <em>canonically</em> Base64url-encoded: the decoded bytes are
 * re-encoded and compared against the input. Java's Base64 decoder is lenient and ignores both
 * {@code =} padding and the unused trailing bits of the final character, so without this check a
 * single token would have many accepted spellings that all decode to the same signed payload.
 *
 * <p>If the secret is rotated, all in-flight tokens become invalid. Clients should treat an
 * {@link CursorErrorCode#INVALID_CURSOR} result as "start from the beginning" and omit {@code pageToken}.
 */
@Component
public class CursorEncoder implements CursorTokenEncoder {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int HMAC_BYTES = 16;
    private static final char FIELD_SEPARATOR = ':';

    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();

    private final byte[] secretBytes;

    public CursorEncoder(@Value("${app.cursor.secret}") String secret) {
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Encodes a keyset cursor position into a signed, opaque token bound to {@code context}.
     *
     * @param context   binding value mixed into the signature but not stored in the token
     * @param createdAt the {@code created_at} timestamp of the last row on the current page
     * @param id        the {@code id} (UUID) of the last row on the current page
     * @return Base64url-encoded signed token
     */
    @Override
    public String encode(String context, Instant createdAt, UUID id) {
        String payload = createdAt.toEpochMilli() + String.valueOf(FIELD_SEPARATOR) + id;
        String signature = hmacPrefix(context, payload);
        String raw = payload + FIELD_SEPARATOR + signature;
        return BASE64_ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes and verifies a cursor token issued for {@code context}.
     *
     * @param context the same binding value used at encode time
     * @param token   the Base64url-encoded token from a previous response's {@code nextPageToken}
     * @return {@link Result.Success} with a {@link ScrollCursor}, or
     *         {@link Result.Failure} with {@link CursorErrorCode#INVALID_CURSOR} if the token
     *         is malformed, not canonically encoded, was issued for a different context, or the
     *         HMAC signature is invalid
     */
    @Override
    public Result<ScrollCursor, CursorErrorCode> decode(String context, String token) {
        String raw = decodeCanonicalBase64(token);
        if (raw == null) {
            return Result.failure(CursorErrorCode.INVALID_CURSOR);
        }

        int lastSeparator = raw.lastIndexOf(FIELD_SEPARATOR);
        if (lastSeparator < 0) {
            return Result.failure(CursorErrorCode.INVALID_CURSOR);
        }
        String payload = raw.substring(0, lastSeparator);
        String providedSignature = raw.substring(lastSeparator + 1);

        if (!constantTimeEquals(hmacPrefix(context, payload), providedSignature)) {
            return Result.failure(CursorErrorCode.INVALID_CURSOR);
        }

        return parsePayload(payload);
    }

    /**
     * Decodes {@code token} only if it is the single canonical Base64url spelling of its bytes,
     * rejecting {@code =} padding and non-zero unused trailing bits.
     *
     * @return the decoded string, or {@code null} when the token is not canonically encoded
     */
    private static String decodeCanonicalBase64(String token) {
        byte[] decoded;
        try {
            decoded = BASE64_DECODER.decode(token);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!BASE64_ENCODER.encodeToString(decoded).equals(token)) {
            return null;
        }
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private static Result<ScrollCursor, CursorErrorCode> parsePayload(String payload) {
        int separator = payload.indexOf(FIELD_SEPARATOR);
        if (separator < 0) {
            return Result.failure(CursorErrorCode.INVALID_CURSOR);
        }
        try {
            long epochMs = Long.parseLong(payload.substring(0, separator));
            UUID id = UUID.fromString(payload.substring(separator + 1));
            return Result.success(new ScrollCursor(Instant.ofEpochMilli(epochMs), id));
        } catch (IllegalArgumentException e) {
            return Result.failure(CursorErrorCode.INVALID_CURSOR);
        }
    }

    /**
     * Computes the first {@value HMAC_BYTES} bytes of HMAC-SHA256 over the length-prefixed
     * {@code context} joined with {@code payload}, as hex.
     */
    private String hmacPrefix(String context, String payload) {
        String signed = context.length()
                + String.valueOf(FIELD_SEPARATOR)
                + context
                + FIELD_SEPARATOR
                + payload;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
            byte[] hmacBytes = mac.doFinal(signed.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(HMAC_BYTES * 2);
            for (int i = 0; i < HMAC_BYTES; i++) {
                hex.append(String.format("%02x", hmacBytes[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }

    /** Constant-time comparison of two hex signatures to prevent timing attacks. */
    private static boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
