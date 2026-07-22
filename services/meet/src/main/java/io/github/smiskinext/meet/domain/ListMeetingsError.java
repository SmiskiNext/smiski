package io.github.smiskinext.meet.domain;

import io.github.smiskinext.shared.domain.CursorErrorCode;
import io.github.smiskinext.shared.domain.DomainError;
import io.github.smiskinext.shared.domain.ErrorCode;

/**
 * Domain errors for listing tenant meetings.
 *
 * <p>Kept separate from {@link MeetingError} because listing failures are read-side and reuse the
 * shared {@link CursorErrorCode} vocabulary rather than the meeting write-side codes.
 */
public sealed interface ListMeetingsError extends DomainError {

    /**
     * The supplied page token is malformed, tampered with, or was issued for a different sort field
     * than the current request.
     */
    record InvalidCursor() implements ListMeetingsError {
        @Override
        public ErrorCode errorCode() {
            return CursorErrorCode.INVALID_CURSOR;
        }
    }
}
