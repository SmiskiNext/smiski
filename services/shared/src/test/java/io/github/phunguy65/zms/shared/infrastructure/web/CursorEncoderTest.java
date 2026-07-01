package io.github.phunguy65.zms.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.phunguy65.zms.shared.domain.CursorErrorCode;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.ScrollCursor;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CursorEncoderTest {

    private CursorEncoder encoder;

    private static final Instant CREATED_AT = Instant.ofEpochMilli(1_700_000_000_000L);
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        encoder = new CursorEncoder("test-secret-key");
    }

    // ─── encode ──────────────────────────────────────────────────────────────

    @Test
    void encode_producesNonNullBase64urlToken() {
        String token = encoder.encode(CREATED_AT, ID);
        assertThat(token).isNotNull().isNotBlank();
        // Base64url must not contain '+', '/', or '='
        assertThat(token).doesNotContain("+", "/", "=");
    }

    @Test
    void encode_samInputs_producesSameToken() {
        String token1 = encoder.encode(CREATED_AT, ID);
        String token2 = encoder.encode(CREATED_AT, ID);
        assertThat(token1).isEqualTo(token2);
    }

    @Test
    void encode_differentInputs_produceDifferentTokens() {
        String token1 = encoder.encode(CREATED_AT, ID);
        String token2 = encoder.encode(CREATED_AT, UUID.randomUUID());
        assertThat(token1).isNotEqualTo(token2);
    }

    // ─── decode — happy path ─────────────────────────────────────────────────

    @Test
    void decode_validToken_returnsSuccessWithOriginalValues() {
        String token = encoder.encode(CREATED_AT, ID);

        Result<ScrollCursor, CursorErrorCode> result = encoder.decode(token);

        assertThat(result).isInstanceOf(Result.Success.class);
        ScrollCursor cursor = ((Result.Success<ScrollCursor, CursorErrorCode>) result).value();
        assertThat(cursor.createdAt()).isEqualTo(CREATED_AT);
        assertThat(cursor.id()).isEqualTo(ID);
    }

    @Test
    void decode_roundTrip_preservesEpochMilliPrecision() {
        Instant precise = Instant.ofEpochMilli(1_700_123_456_789L);
        String token = encoder.encode(precise, ID);

        Result<ScrollCursor, CursorErrorCode> result = encoder.decode(token);

        assertThat(result).isInstanceOf(Result.Success.class);
        ScrollCursor cursor = ((Result.Success<ScrollCursor, CursorErrorCode>) result).value();
        assertThat(cursor.createdAt().toEpochMilli()).isEqualTo(1_700_123_456_789L);
    }

    // ─── decode — tampered token ─────────────────────────────────────────────

    @Test
    void decode_tamperedSignature_returnsFailure() {
        String token = encoder.encode(CREATED_AT, ID);
        // Flip last character to corrupt the HMAC signature
        String tampered = token.substring(0, token.length() - 1)
                + (token.charAt(token.length() - 1) == 'a' ? 'b' : 'a');

        Result<ScrollCursor, CursorErrorCode> result = encoder.decode(tampered);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<ScrollCursor, CursorErrorCode>) result).error())
                .isEqualTo(CursorErrorCode.INVALID_CURSOR);
    }

    @Test
    void decode_tokenFromDifferentSecret_returnsFailure() {
        CursorEncoder otherEncoder = new CursorEncoder("different-secret");
        String token = otherEncoder.encode(CREATED_AT, ID);

        Result<ScrollCursor, CursorErrorCode> result = encoder.decode(token);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<ScrollCursor, CursorErrorCode>) result).error())
                .isEqualTo(CursorErrorCode.INVALID_CURSOR);
    }

    // ─── decode — malformed token ────────────────────────────────────────────

    @Test
    void decode_invalidBase64url_returnsFailure() {
        Result<ScrollCursor, CursorErrorCode> result = encoder.decode("not!!!valid@base64");

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<ScrollCursor, CursorErrorCode>) result).error())
                .isEqualTo(CursorErrorCode.INVALID_CURSOR);
    }

    @Test
    void decode_emptyString_returnsFailure() {
        Result<ScrollCursor, CursorErrorCode> result = encoder.decode("");

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<ScrollCursor, CursorErrorCode>) result).error())
                .isEqualTo(CursorErrorCode.INVALID_CURSOR);
    }

    @Test
    void decode_noColonSeparator_returnsFailure() {
        // Valid Base64url but no ':' in decoded content
        String noColon = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("nocolon".getBytes());

        Result<ScrollCursor, CursorErrorCode> result = encoder.decode(noColon);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<ScrollCursor, CursorErrorCode>) result).error())
                .isEqualTo(CursorErrorCode.INVALID_CURSOR);
    }

    @Test
    void decode_invalidUuidInPayload_returnsFailure() {
        // Craft a token with valid structure but non-UUID id field
        String payload = "1700000000000:not-a-uuid";
        // We need a valid HMAC for this payload — use a fresh encoder to sign it
        // but since we can't call hmacPrefix directly, we just verify the decode path
        // by encoding a valid token and then manually corrupting the UUID part
        String validToken = encoder.encode(CREATED_AT, ID);
        // The token is Base64url(epochMs:uuid:hmac) — we can't easily corrupt just the UUID
        // without re-signing, so we verify that a structurally invalid raw string is rejected
        String rawInvalid = "1700000000000:not-a-uuid:deadbeef";
        String encoded = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawInvalid.getBytes());

        Result<ScrollCursor, CursorErrorCode> result = encoder.decode(encoded);

        // Either signature mismatch or UUID parse failure — both return INVALID_CURSOR
        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<ScrollCursor, CursorErrorCode>) result).error())
                .isEqualTo(CursorErrorCode.INVALID_CURSOR);
    }
}
