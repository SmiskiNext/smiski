package io.github.smiskinext.meetingmanagement.domain.model.valueobject;

import io.github.smiskinext.meetingmanagement.domain.model.ParticipantRole;
import io.github.smiskinext.shared.domain.ValueObject;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Request for generating a LiveKit JWT access token.
 *
 * <p>The {@code meetingSettings} field is required for PARTICIPANT tokens to derive
 * permission restrictions (microphone, camera, screen share, chat). HOST and GUEST
 * tokens use fixed permission policies regardless of meeting settings, but the field
 * is still accepted for consistency across all call sites.
 *
 * @param roomName              the LiveKit room to join
 * @param identity              the participant identity string
 * @param displayName           display name shown in the room
 * @param role                  participant role (HOST, PARTICIPANT, GUEST)
 * @param participantAttributes attributes attached to the token
 * @param meetingSettings       current meeting settings (nullable for backwards compat,
 *                              but should be provided for PARTICIPANT tokens)
 */
public record LiveKitTokenRequest(
        LiveKitRoomName roomName,
        LiveKitIdentity identity,
        String displayName,
        ParticipantRole role,
        ParticipantAttributes participantAttributes,
        @Nullable MeetingSettings meetingSettings)
        implements ValueObject {

    public LiveKitTokenRequest {
        Objects.requireNonNull(roomName, "roomName must not be null");
        Objects.requireNonNull(identity, "identity must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(participantAttributes, "participantAttributes must not be null");
    }

    /**
     * Convenience constructor without meeting settings (for backwards compatibility).
     * New call sites should use the full constructor with meeting settings.
     */
    public LiveKitTokenRequest(
            LiveKitRoomName roomName,
            LiveKitIdentity identity,
            String displayName,
            ParticipantRole role,
            ParticipantAttributes participantAttributes) {
        this(roomName, identity, displayName, role, participantAttributes, null);
    }
}
