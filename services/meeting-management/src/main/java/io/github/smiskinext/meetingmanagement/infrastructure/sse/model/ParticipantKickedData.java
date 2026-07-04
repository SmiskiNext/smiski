package io.github.smiskinext.meetingmanagement.infrastructure.sse.model;

import java.util.Objects;

/**
 * SSE payload for a {@code participant_kicked} event, emitted to meeting hosts after a
 * successful kick action.
 *
 * @param meetingId     the meeting from which the participant was removed
 * @param kickedUserId  the kicked user's ID, or null if the target was a guest
 * @param displayName   the display name of the kicked participant
 */
public record ParticipantKickedData(String meetingId, String kickedUserId, String displayName)
        implements SseEventData {
    public ParticipantKickedData {
        Objects.requireNonNull(meetingId, "meetingId cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
    }
}
