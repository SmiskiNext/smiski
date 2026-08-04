package io.github.smiskinext.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.shared.domain.CursorErrorCode;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.ScrollCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CursorEncoderTest {

    private static final String SECRET = "unit-test-cursor-secret";
    private static final String CONTEXT = "C";
    private static final Instant POSITION_TIMESTAMP = Instant.ofEpochMilli(1_785_827_034_719L);
    private static final UUID POSITION_ID = UUID.fromString("17e9997c-8d99-4cb6-b333-b583b12efd06");

    private static final String BASE64_URL_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    private final CursorEncoder encoder = new CursorEncoder(SECRET);

    @Test
    void roundTripsSignedPosition() {
        String token = encoder.encode(CONTEXT, POSITION_TIMESTAMP, POSITION_ID);

        ScrollCursor cursor = success(encoder.decode(CONTEXT, token));

        assertThat(cursor.createdAt()).isEqualTo(POSITION_TIMESTAMP);
        assertThat(cursor.id()).isEqualTo(POSITION_ID);
    }

    @Test
    void rejectsTokenDecodedUnderDifferentContext() {
        String token = encoder.encode(CONTEXT, POSITION_TIMESTAMP, POSITION_ID);

        assertThat(failure(encoder.decode("S", token))).isEqualTo(CursorErrorCode.INVALID_CURSOR);
    }

    @Test
    void distinctContextsProduceDistinctTokensOfEqualLength() {
        List<String> contexts = List.of("", "C", "S", ":", "C:S", "1:C", "CC");

        List<String> tokens = contexts.stream()
                .map(context -> encoder.encode(context, POSITION_TIMESTAMP, POSITION_ID))
                .toList();

        assertThat(tokens).doesNotHaveDuplicates();
        assertThat(tokens)
                .extracting(String::length)
                .containsOnly(tokens.getFirst().length());
    }

    @Test
    void rejectsTokenWithNonZeroUnusedTrailingBits() {
        String token = encoder.encode(CONTEXT, POSITION_TIMESTAMP, POSITION_ID);
        String mutated = mutateUnusedTrailingBits(token);

        assertThat(Base64.getUrlDecoder().decode(mutated))
                .as("mutation must only flip bits the base64 decoder ignores")
                .isEqualTo(Base64.getUrlDecoder().decode(token));
        assertThat(mutated).isNotEqualTo(token);

        assertThat(failure(encoder.decode(CONTEXT, mutated)))
                .isEqualTo(CursorErrorCode.INVALID_CURSOR);
    }

    @Test
    void rejectsPaddedToken() {
        String token = encoder.encode(CONTEXT, POSITION_TIMESTAMP, POSITION_ID);

        assertThat(failure(encoder.decode(CONTEXT, token + "=")))
                .isEqualTo(CursorErrorCode.INVALID_CURSOR);
    }

    @Test
    void rejectsTamperedSignature() {
        String token = encoder.encode(CONTEXT, POSITION_TIMESTAMP, POSITION_ID);
        String raw = decodeRaw(token);
        String tamperedRaw =
                raw.substring(0, raw.length() - 1) + flipHexDigit(raw.charAt(raw.length() - 1));

        assertThat(failure(encoder.decode(CONTEXT, encodeRaw(tamperedRaw))))
                .isEqualTo(CursorErrorCode.INVALID_CURSOR);
    }

    @Test
    void rejectsTamperedPosition() {
        String token = encoder.encode(CONTEXT, POSITION_TIMESTAMP, POSITION_ID);
        String raw = decodeRaw(token);
        String tamperedRaw = raw.replace(
                String.valueOf(POSITION_TIMESTAMP.toEpochMilli()),
                String.valueOf(POSITION_TIMESTAMP.toEpochMilli() + 1));

        assertThat(failure(encoder.decode(CONTEXT, encodeRaw(tamperedRaw))))
                .isEqualTo(CursorErrorCode.INVALID_CURSOR);
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        String foreignToken = new CursorEncoder("another-cursor-secret")
                .encode(CONTEXT, POSITION_TIMESTAMP, POSITION_ID);

        assertThat(failure(encoder.decode(CONTEXT, foreignToken)))
                .isEqualTo(CursorErrorCode.INVALID_CURSOR);
    }

    @Test
    void rejectsMalformedTokens() {
        assertThat(failure(encoder.decode(CONTEXT, "not base64!")))
                .isEqualTo(CursorErrorCode.INVALID_CURSOR);
        assertThat(failure(encoder.decode(CONTEXT, encodeRaw("no-separator-present"))))
                .isEqualTo(CursorErrorCode.INVALID_CURSOR);
        assertThat(failure(encoder.decode(CONTEXT, ""))).isEqualTo(CursorErrorCode.INVALID_CURSOR);
    }

    private static String mutateUnusedTrailingBits(String token) {
        char last = token.charAt(token.length() - 1);
        int alphabetIndex = BASE64_URL_ALPHABET.indexOf(last);
        return token.substring(0, token.length() - 1)
                + BASE64_URL_ALPHABET.charAt(alphabetIndex ^ 1);
    }

    private static char flipHexDigit(char digit) {
        return digit == '0' ? '1' : '0';
    }

    private static String decodeRaw(String token) {
        return new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
    }

    private static String encodeRaw(String raw) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static ScrollCursor success(Result<ScrollCursor, CursorErrorCode> result) {
        return ((Result.Success<ScrollCursor, CursorErrorCode>) result).value();
    }

    private static CursorErrorCode failure(Result<ScrollCursor, CursorErrorCode> result) {
        return ((Result.Failure<ScrollCursor, CursorErrorCode>) result).error();
    }
}
