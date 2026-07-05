package io.github.smiskinext.meetingmanagement.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;
import java.util.Objects;

/**
 * LiveKit participant session ID (e.g. {@code "PA_aBj5MmUSWBFK"}).
 *
 * <p>Assigned by LiveKit per connection instance. Changes on reconnect.
 * Used to map {@code participant_left} webhook events back to the correct
 * {@code participation_logs} row.
 */
public record LiveKitParticipantSid(String value) implements ValueObject {

    public LiveKitParticipantSid {
        Objects.requireNonNull(value, "LiveKitParticipantSid must not be null");
        if (value.isBlank())
            throw new IllegalArgumentException("LiveKitParticipantSid must not be blank");
    }

    public static LiveKitParticipantSid of(String raw) {
        return new LiveKitParticipantSid(raw);
    }
}
