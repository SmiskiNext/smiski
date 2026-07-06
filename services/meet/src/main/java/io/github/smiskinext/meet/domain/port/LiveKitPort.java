package io.github.smiskinext.meet.domain.port;

import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.ParticipantRole;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitTokenRequest;
import io.github.smiskinext.meet.domain.model.valueobject.ParticipantGrants;
import io.github.smiskinext.shared.domain.Result;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Port for interacting with the LiveKit media server.
 */
public interface LiveKitPort {

    /**
     * Generates a signed JWT token for a participant to join a LiveKit room.
     *
     * <p>Permission matrix by role:
     * <ul>
     *   <li>{@link ParticipantRole#HOST} — roomAdmin, canPublish, canPublishData, canSubscribe,
     *       canUpdateOwnMetadata</li>
     *   <li>{@link ParticipantRole#PARTICIPANT} — canPublish, canPublishData, canSubscribe,
     *       canUpdateOwnMetadata</li>
     *   <li>{@link ParticipantRole#GUEST} — canSubscribe, canUpdateOwnMetadata (no media
     *       publish)</li>
     * </ul>
     *
     * @param request token generation request, including identity, display name, role, and
     *     token-time participant attributes
     * @return {@link Result.Success} with the signed JWT token, or {@link Result.Failure} with
     * {@link MeetingError.LiveKitUnavailable} if the token cannot be generated
     */
    Result<String, MeetingError> generateToken(LiveKitTokenRequest request);

    /**
     * Updates a connected participant's permissions mid-session without disconnecting them.
     *
     * @param roomName the LiveKit room containing the participant
     * @param identity the participant's identity string as embedded in their JWT ({@code sub} claim)
     * @param grants   the new permission set to apply
     * @return {@link Result.Success} on success, or {@link Result.Failure} with
     * {@link MeetingError.LiveKitUnavailable} if the server is unreachable
     */
    Result<Void, MeetingError> updateParticipantPermissions(
            LiveKitRoomName roomName, String identity, ParticipantGrants grants);

    /**
     * Updates a connected participant's runtime profile without changing the history snapshot
     * stored in meeting-management.
     *
     * @param roomName  the LiveKit room containing the participant
     * @param identity  the participant's identity string as embedded in their JWT ({@code sub} claim)
     * @param role      the participant role to keep in sync with LiveKit attributes/permissions
     * @param fullName  the display name to expose in the active room
     * @param avatarUrl the avatar URL to expose in LiveKit attributes; null clears the attribute
     * @return {@link Result.Success} on success, or {@link Result.Failure} with
     * {@link MeetingError.LiveKitUnavailable} if the server is unreachable or rejects the update
     */
    Result<Void, MeetingError> updateParticipantProfile(
            LiveKitRoomName roomName,
            String identity,
            ParticipantRole role,
            String fullName,
            @Nullable String avatarUrl);

    /**
     * Deletes a LiveKit room and disconnects all participants.
     *
     * @return {@link Result.Success} on success, or {@link Result.Failure} with
     * {@link MeetingError.LiveKitUnavailable} if the server is unreachable
     */
    Result<Void, MeetingError> deleteRoom(LiveKitRoomName roomName);

    /**
     * Forcibly removes a connected participant from a LiveKit room without ending the room.
     *
     * <p>Used by the host kick flow. The participant's {@code participant_left} webhook will
     * fire as normal, closing the corresponding participation log through the existing leave
     * lifecycle.
     *
     * <p>If the participant is no longer in the room (already left), this method returns
     * {@link Result.Success} — the kick is idempotent at the room level.
     *
     * @param roomName the LiveKit room containing the participant
     * @param identity the participant's identity string as embedded in their JWT
     * @return {@link Result.Success} if the participant was removed or is already gone, or
     * {@link Result.Failure} with {@link MeetingError.LiveKitUnavailable} on network error
     */
    Result<Void, MeetingError> removeParticipant(LiveKitRoomName roomName, String identity);

    /**
     * Mutes a single published track for a participant by resolving the track SID server-side.
     *
     * @param roomName the LiveKit room containing the participant
     * @param identity the participant's identity string
     * @param source   the track source type ({@code "microphone"} or {@code "camera"})
     * @return {@link Result.Success} on success, or {@link Result.Failure} with
     * {@link MeetingError.TrackNotFound} if no published track of the given source exists,
     * {@link MeetingError.LiveKitUnavailable} on network error
     */
    Result<Void, MeetingError> muteParticipantTrack(
            LiveKitRoomName roomName, String identity, String source);

    /**
     * Mutes the microphone tracks of multiple participants using best-effort semantics.
     *
     * <p>Iterates through all identities and attempts to mute each participant's microphone.
     * Participants who have already left (404) are skipped silently.
     *
     * @param roomName   the LiveKit room
     * @param identities the LiveKit identities to mute
     * @return {@link Result.Success} if at least one mute succeeded or all were non-fatal skips,
     * {@link Result.Failure} with {@link MeetingError.LiveKitUnavailable} only if every call fails
     */
    Result<Void, MeetingError> muteAllParticipantMicTracks(
            LiveKitRoomName roomName, List<String> identities);

    /**
     * Updates the room-level metadata for a LiveKit room.
     *
     * <p>Used to broadcast room-level state signals to all connected participants
     * through the room metadata channel.
     *
     * @param roomName the LiveKit room whose metadata should be updated
     * @param metadata the new metadata JSON string to set on the room
     * @return {@link Result.Success} on success, or {@link Result.Failure} with
     * {@link MeetingError.LiveKitUnavailable} if the server is unreachable or rejects the update
     */
    Result<Void, MeetingError> updateRoomMetadata(LiveKitRoomName roomName, String metadata);
}
