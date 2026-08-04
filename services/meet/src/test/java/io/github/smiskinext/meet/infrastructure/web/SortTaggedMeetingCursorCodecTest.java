package io.github.smiskinext.meet.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meet.domain.port.MeetingCursorCodec;
import io.github.smiskinext.meet.domain.projection.MeetingSortField;
import io.github.smiskinext.shared.domain.CursorErrorCode;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.infrastructure.web.CursorEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SortTaggedMeetingCursorCodecTest {

    private static final int PREFIX_LENGTH = 2;

    private final SortTaggedMeetingCursorCodec codec =
            new SortTaggedMeetingCursorCodec(new CursorEncoder("unit-test-cursor-secret"));

    @Test
    void roundTripsCreatedAtSort() {
        Instant ts = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UUID id = UUID.randomUUID();

        String token = codec.encode(MeetingSortField.CREATED_AT, ts, id);
        MeetingCursorCodec.DecodedCursor decoded = success(codec.decode(token));

        assertThat(decoded.sort()).isEqualTo(MeetingSortField.CREATED_AT);
        assertThat(decoded.sortValue()).isEqualTo(ts);
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    void roundTripsStartTimeSort() {
        Instant ts = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UUID id = UUID.randomUUID();

        String token = codec.encode(MeetingSortField.START_TIME, ts, id);
        MeetingCursorCodec.DecodedCursor decoded = success(codec.decode(token));

        assertThat(decoded.sort()).isEqualTo(MeetingSortField.START_TIME);
        assertThat(decoded.sortValue()).isEqualTo(ts);
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    void tamperedSignatureIsRejected() {
        String token = codec.encode(MeetingSortField.CREATED_AT, Instant.now(), UUID.randomUUID());
        String signed = decodeInnerToken(token);
        String tamperedSigned = signed.substring(0, signed.length() - 1)
                + (signed.charAt(signed.length() - 1) == '0' ? '1' : '0');

        String tampered = token.substring(0, PREFIX_LENGTH) + encodeInnerToken(tamperedSigned);

        assertThat(failure(codec.decode(tampered))).isEqualTo(CursorErrorCode.INVALID_CURSOR);
    }

    @Test
    void nonCanonicallyEncodedTokenIsRejected() {
        String token = codec.encode(MeetingSortField.CREATED_AT, Instant.now(), UUID.randomUUID());

        assertThat(failure(codec.decode(token + "="))).isEqualTo(CursorErrorCode.INVALID_CURSOR);
    }

    @Test
    void rewrittenSortTagIsRejected() {
        String createdToken =
                codec.encode(MeetingSortField.CREATED_AT, Instant.now(), UUID.randomUUID());
        String retagged = "S" + createdToken.substring(1);

        assertThat(failure(codec.decode(retagged))).isEqualTo(CursorErrorCode.INVALID_CURSOR);
    }

    @Test
    void missingSeparatorIsRejected() {
        assertThat(failure(codec.decode("Cnoseparator"))).isEqualTo(CursorErrorCode.INVALID_CURSOR);
    }

    @Test
    void unknownSortTagIsRejected() {
        String token = codec.encode(MeetingSortField.CREATED_AT, Instant.now(), UUID.randomUUID());
        String retagged = "X" + token.substring(1);

        assertThat(failure(codec.decode(retagged))).isEqualTo(CursorErrorCode.INVALID_CURSOR);
    }

    @Test
    void tooShortTokenIsRejected() {
        assertThat(failure(codec.decode("C"))).isEqualTo(CursorErrorCode.INVALID_CURSOR);
        assertThat(failure(codec.decode("C."))).isEqualTo(CursorErrorCode.INVALID_CURSOR);
        assertThat(failure(codec.decode(""))).isEqualTo(CursorErrorCode.INVALID_CURSOR);
    }

    @Test
    void tokenIssuedForOneSortDecodesWithThatSortTag() {
        Instant ts = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UUID id = UUID.randomUUID();

        String createdToken = codec.encode(MeetingSortField.CREATED_AT, ts, id);
        String startToken = codec.encode(MeetingSortField.START_TIME, ts, id);

        assertThat(success(codec.decode(createdToken)).sort())
                .isEqualTo(MeetingSortField.CREATED_AT);
        assertThat(success(codec.decode(startToken)).sort()).isEqualTo(MeetingSortField.START_TIME);
        assertThat(createdToken).isNotEqualTo(startToken);
    }

    @Test
    void sortTagIsCoveredBySignatureRatherThanPayload() {
        Instant ts = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UUID id = UUID.randomUUID();

        String createdToken = codec.encode(MeetingSortField.CREATED_AT, ts, id);
        String startToken = codec.encode(MeetingSortField.START_TIME, ts, id);

        assertThat(createdToken).hasSameSizeAs(startToken);
        assertThat(decodeInnerToken(createdToken)).isNotEqualTo(decodeInnerToken(startToken));
    }

    private static String decodeInnerToken(String token) {
        byte[] decoded = Base64.getUrlDecoder().decode(token.substring(PREFIX_LENGTH));
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private static String encodeInnerToken(String signed) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(signed.getBytes(StandardCharsets.UTF_8));
    }

    private static MeetingCursorCodec.DecodedCursor success(
            Result<MeetingCursorCodec.DecodedCursor, CursorErrorCode> result) {
        return ((Result.Success<MeetingCursorCodec.DecodedCursor, CursorErrorCode>) result).value();
    }

    private static CursorErrorCode failure(
            Result<MeetingCursorCodec.DecodedCursor, CursorErrorCode> result) {
        return ((Result.Failure<MeetingCursorCodec.DecodedCursor, CursorErrorCode>) result).error();
    }
}
