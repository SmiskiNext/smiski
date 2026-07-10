package io.github.smiskinext.record.domain;

import io.github.smiskinext.record.domain.model.RecordingStatus;
import io.github.smiskinext.record.domain.model.valueobject.RecordingId;
import io.github.smiskinext.shared.domain.DomainError;
import io.github.smiskinext.shared.domain.ErrorCode;

import java.util.UUID;

/**
 * Domain errors for the record bounded context.
 *
 * <p>Each error exposes its {@link ErrorCode} and the positional arguments used to interpolate the
 * localized {@code detail} message (bundle key {@code error.<code>.detail}); the text is resolved
 * from the {@code messages/record} bundle at the HTTP boundary.
 */
public sealed interface RecordError extends DomainError {

    record InvalidRecordingTransition(RecordingStatus from, RecordingStatus to)
            implements RecordError {
        @Override
        public ErrorCode errorCode() {
            return RecordErrorCode.INVALID_RECORDING_TRANSITION;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {from, to};
        }
    }

    record RecordingAlreadyActive(UUID meetingId) implements RecordError {
        @Override
        public ErrorCode errorCode() {
            return RecordErrorCode.RECORDING_ALREADY_ACTIVE;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {meetingId};
        }
    }

    record NoActiveRecording(UUID meetingId) implements RecordError {
        @Override
        public ErrorCode errorCode() {
            return RecordErrorCode.NO_ACTIVE_RECORDING;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {meetingId};
        }
    }

    record RecordingNotFound(RecordingId id) implements RecordError {
        @Override
        public ErrorCode errorCode() {
            return RecordErrorCode.RECORDING_NOT_FOUND;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {id.value()};
        }
    }
}
