package io.github.smiskinext.meet.domain.port;

import io.github.smiskinext.meet.domain.projection.MeetingSortField;
import io.github.smiskinext.shared.domain.CursorErrorCode;
import io.github.smiskinext.shared.domain.Result;

import java.time.Instant;
import java.util.UUID;

/**
 * Encodes and decodes opaque keyset cursor tokens that additionally bind the {@link MeetingSortField}
 * they were issued for.
 *
 * <p>The implementation wraps the shared HMAC cursor encoder so that a token issued under one sort
 * mode cannot be silently replayed under another; decoding surfaces the embedded sort field so the
 * use case can reject a mismatch.
 */
public interface MeetingCursorCodec {

    /**
     * Encodes a keyset position and its sort field into a signed, opaque token.
     *
     * @param sort      the sort field the token is issued for
     * @param sortValue the ordering timestamp of the last row on the current page
     * @param id        the id of the last row on the current page
     * @return an opaque token safe to expose as {@code nextPageToken}
     */
    String encode(MeetingSortField sort, Instant sortValue, UUID id);

    /**
     * Decodes and verifies a token, returning its embedded sort field and keyset position.
     *
     * @param token the opaque token from a previous response's {@code nextPageToken}
     * @return the decoded cursor, or {@link CursorErrorCode#INVALID_CURSOR} when the token is
     *     malformed, tampered with, or its signature does not verify
     */
    Result<DecodedCursor, CursorErrorCode> decode(String token);

    /**
     * A decoded, signature-verified cursor.
     *
     * @param sort      the sort field embedded in the token
     * @param sortValue the ordering timestamp to scroll past
     * @param id        the id to scroll past
     */
    record DecodedCursor(MeetingSortField sort, Instant sortValue, UUID id) {}
}
