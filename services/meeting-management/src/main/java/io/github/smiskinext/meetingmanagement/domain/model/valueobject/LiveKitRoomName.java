package io.github.smiskinext.meetingmanagement.domain.model.valueobject;

import io.github.phunguy65.zms.shared.domain.ValueObject;
import io.github.phunguy65.zms.shared.domain.valueobject.MeetingId;
import java.util.Objects;

/**
 * LiveKit room name derived from the meeting UUID.
 *
 * <p>Format: {@code "meeting-<uuid>"} — stable, predictable, and unique per meeting.
 */
public record LiveKitRoomName(String value) implements ValueObject {

    public LiveKitRoomName {
        Objects.requireNonNull(value, "LiveKitRoomName must not be null");
        if (value.isBlank())
            throw new IllegalArgumentException("LiveKitRoomName must not be blank");
    }

    public static LiveKitRoomName fromMeetingId(MeetingId meetingId) {
        return new LiveKitRoomName("meeting-" + meetingId.value());
    }

    public static LiveKitRoomName of(String raw) {
        return new LiveKitRoomName(raw);
    }
}
