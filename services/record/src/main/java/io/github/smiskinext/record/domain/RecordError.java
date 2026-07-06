package io.github.smiskinext.record.domain;

import io.github.smiskinext.record.domain.model.RecordingStatus;
import io.github.smiskinext.record.domain.model.valueobject.RecordingId;
import io.github.smiskinext.shared.domain.DomainError;

import java.util.UUID;

/**
 * Domain errors for the record bounded context.
 */
public sealed interface RecordError extends DomainError {

    record InvalidRecordingTransition(RecordingStatus from, RecordingStatus to)
            implements RecordError {
        @Override
        public String message() {
            return "Cannot transition recording from " + from + " to " + to;
        }
    }

    record RecordingAlreadyActive(UUID meetingId) implements RecordError {
        @Override
        public String message() {
            return "A recording is already active for meeting: " + meetingId;
        }
    }

    record NoActiveRecording(UUID meetingId) implements RecordError {
        @Override
        public String message() {
            return "No active recording found for meeting: " + meetingId;
        }
    }

    record RecordingNotFound(RecordingId id) implements RecordError {
        @Override
        public String message() {
            return "Recording not found: " + id.value();
        }
    }
}
