package io.github.smiskinext.meet.domain.model.valueobject;

import io.github.smiskinext.meet.domain.model.ParticipantRole;
import io.github.smiskinext.shared.domain.ValueObject;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Request for generating a LiveKit JWT access token.
 *
 * <p>The {@code meetingSettings} field is required for PARTICIPANT tokens to derive
 * permission restrictions (microphone, camera, screen share, chat). HOST tokens use
 * fixed permission policies regardless of meeting settings, but the field is still
 * accepted for consistency across all call sites.
 *
 * <p>The {@code tenantId} is embedded into the token's LiveKit room configuration metadata so
 * that room and participant webhooks — which carry no tenant header — can resolve the owning
 * tenant from {@code room.metadata}.
 *
 * @param roomName              the LiveKit room to join
 * @param identity              the participant identity string
 * @param displayName           display name shown in the room
 * @param role                  participant role (HOST, PARTICIPANT)
 * @param participantAttributes attributes attached to the token
 * @param tenantId              owning tenant identifier embedded as room metadata
 * @param meetingSettings       current meeting settings (nullable for backwards compat,
 *                              but should be provided for PARTICIPANT tokens)
 */
public record LiveKitTokenRequest(
        LiveKitRoomName roomName,
        LiveKitIdentity identity,
        String displayName,
        ParticipantRole role,
        ParticipantAttributes participantAttributes,
        String tenantId,
        @Nullable MeetingSettings meetingSettings)
        implements ValueObject {

    public LiveKitTokenRequest {
        Objects.requireNonNull(roomName, "roomName must not be null");
        Objects.requireNonNull(identity, "identity must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(participantAttributes, "participantAttributes must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
    }

    /**
     * Convenience constructor without meeting settings.
     * New call sites should use the full constructor with meeting settings.
     */
    public LiveKitTokenRequest(
            LiveKitRoomName roomName,
            LiveKitIdentity identity,
            String displayName,
            ParticipantRole role,
            ParticipantAttributes participantAttributes,
            String tenantId) {
        this(roomName, identity, displayName, role, participantAttributes, tenantId, null);
    }
}
