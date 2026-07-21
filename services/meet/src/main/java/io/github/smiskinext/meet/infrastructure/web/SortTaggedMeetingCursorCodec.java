package io.github.smiskinext.meet.infrastructure.web;

import io.github.smiskinext.meet.domain.port.MeetingCursorCodec;
import io.github.smiskinext.meet.domain.projection.MeetingSortField;
import io.github.smiskinext.shared.domain.CursorErrorCode;
import io.github.smiskinext.shared.domain.CursorTokenEncoder;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.ScrollCursor;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Sort-tagged cursor codec that wraps the shared HMAC {@link CursorTokenEncoder}.
 *
 * <p>The shared encoder signs only the keyset position {@code (Instant, UUID)} and carries no sort
 * context. This codec prepends a one-character sort tag to the shared token so decoding can recover
 * the sort field the token was issued for. Token format:
 *
 * <pre>
 * token = "&lt;tag&gt;.&lt;sharedToken&gt;"  where tag ∈ {C, S}
 * </pre>
 *
 * <p>The {@code .} separator is outside the Base64url alphabet used by the shared token, so the tag
 * can be split off unambiguously. The shared HMAC still protects the keyset position against
 * tampering; a token whose separator, tag, or inner signature is malformed decodes to
 * {@link CursorErrorCode#INVALID_CURSOR}. The shared encoder is used unmodified.
 */
@Component
public class SortTaggedMeetingCursorCodec implements MeetingCursorCodec {

    private static final char SEPARATOR = '.';
    private static final char CREATED_AT_TAG = 'C';
    private static final char START_TIME_TAG = 'S';

    private final CursorTokenEncoder delegate;

    public SortTaggedMeetingCursorCodec(CursorTokenEncoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public String encode(MeetingSortField sort, Instant sortValue, UUID id) {
        return tagOf(sort) + Character.toString(SEPARATOR) + delegate.encode(sortValue, id);
    }

    @Override
    public Result<DecodedCursor, CursorErrorCode> decode(String token) {
        if (token.length() < 2 || token.charAt(1) != SEPARATOR) {
            return Result.failure(CursorErrorCode.INVALID_CURSOR);
        }

        MeetingSortField sort = sortOf(token.charAt(0));
        if (sort == null) {
            return Result.failure(CursorErrorCode.INVALID_CURSOR);
        }

        String innerToken = token.substring(2);
        return delegate.decode(innerToken)
                .fold(cursor -> Result.success(toDecodedCursor(sort, cursor)), Result::failure);
    }

    private static DecodedCursor toDecodedCursor(MeetingSortField sort, ScrollCursor cursor) {
        return new DecodedCursor(sort, cursor.createdAt(), cursor.id());
    }

    private static char tagOf(MeetingSortField sort) {
        return switch (sort) {
            case CREATED_AT -> CREATED_AT_TAG;
            case START_TIME -> START_TIME_TAG;
        };
    }

    private static MeetingSortField sortOf(char tag) {
        return switch (tag) {
            case CREATED_AT_TAG -> MeetingSortField.CREATED_AT;
            case START_TIME_TAG -> MeetingSortField.START_TIME;
            default -> null;
        };
    }
}
