package io.github.smiskinext.meet.infrastructure.web;

import io.github.smiskinext.meet.domain.port.MeetingCursorCodec;
import io.github.smiskinext.meet.domain.projection.MeetingSortField;
import io.github.smiskinext.shared.domain.CursorErrorCode;
import io.github.smiskinext.shared.domain.CursorTokenEncoder;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.ScrollCursor;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Sort-tagged cursor codec that wraps the shared HMAC {@link CursorTokenEncoder}.
 *
 * <p>The shared encoder signs the keyset position {@code (Instant, UUID)} together with a
 * caller-supplied context. This codec prepends a one-character sort tag to the shared token so
 * decoding can recover the sort field the token was issued for, and passes that same tag as the
 * encoder's context so the tag is covered by the signature. Token format:
 *
 * <pre>
 * token = "&lt;tag&gt;.&lt;sharedToken&gt;"  where tag ∈ {C, S}
 * </pre>
 *
 * <p>The {@code .} separator is outside the Base64url alphabet used by the shared token, so the tag
 * can be split off unambiguously. Because the tag is signed as context rather than stored in the
 * payload, rewriting it invalidates the signature and the token length stays unchanged. A token
 * whose separator, tag, encoding, or signature is malformed decodes to
 * {@link CursorErrorCode#INVALID_CURSOR}. The shared encoder is used unmodified.
 */
@Component
public class SortTaggedMeetingCursorCodec implements MeetingCursorCodec {

    private static final char SEPARATOR = '.';
    private static final char CREATED_AT_TAG = 'C';
    private static final char START_TIME_TAG = 'S';
    private static final int TAG_LENGTH = 1;
    private static final int PREFIX_LENGTH = TAG_LENGTH + 1;

    private final CursorTokenEncoder delegate;

    public SortTaggedMeetingCursorCodec(CursorTokenEncoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public String encode(MeetingSortField sort, Instant sortValue, UUID id) {
        char tag = tagOf(sort);
        return tag + Character.toString(SEPARATOR) + delegate.encode(contextOf(tag), sortValue, id);
    }

    @Override
    public Result<DecodedCursor, CursorErrorCode> decode(String token) {
        if (token.length() <= PREFIX_LENGTH || token.charAt(TAG_LENGTH) != SEPARATOR) {
            return Result.failure(CursorErrorCode.INVALID_CURSOR);
        }

        char tag = token.charAt(0);
        MeetingSortField sort = sortOf(tag);
        if (sort == null) {
            return Result.failure(CursorErrorCode.INVALID_CURSOR);
        }

        return delegate.decode(contextOf(tag), token.substring(PREFIX_LENGTH))
                .fold(cursor -> Result.success(toDecodedCursor(sort, cursor)), Result::failure);
    }

    private static String contextOf(char tag) {
        return Character.toString(tag);
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

    private static @Nullable MeetingSortField sortOf(char tag) {
        return switch (tag) {
            case CREATED_AT_TAG -> MeetingSortField.CREATED_AT;
            case START_TIME_TAG -> MeetingSortField.START_TIME;
            default -> null;
        };
    }
}
