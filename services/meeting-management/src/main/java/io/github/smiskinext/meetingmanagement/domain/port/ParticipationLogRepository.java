package io.github.smiskinext.meetingmanagement.domain.port;

import io.github.smiskinext.meetingmanagement.domain.model.ParticipationLog;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitParticipantSid;
import io.github.smiskinext.meetingmanagement.domain.projection.ParticipantSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipationLogRepository {

    ParticipationLog save(ParticipationLog log);

    /**
     * Finds the active (not yet left) session by LiveKit participant SID.
     * Primary lookup path for {@code participant_left} webhook handler.
     */
    Optional<ParticipationLog> findActiveBySid(LiveKitParticipantSid sid);

    /**
     * Finds the active (not yet left) session by meeting and LiveKit identity.
     * Used by {@code participant_joined} webhook handler to assign the SID.
     */
    Optional<ParticipationLog> findActiveByMeetingIdAndIdentity(
            UUID meetingId, LiveKitIdentity identity);
    /**
     * Returns the count of currently active (not yet left) participants for a meeting.
     */
    long countActiveByMeetingId(UUID meetingId);

    /**
     * Returns all active (not yet left) participation logs for a meeting.
     * Used by {@code room_finished} webhook and {@code EndMeetingUseCase} to bulk-close
     * any remaining open sessions.
     */
    List<ParticipationLog> findActiveByMeetingId(UUID meetingId);

    /**
     * Returns all active (not yet left) participation logs for a registered user.
     * Used by the user-profile sync consumer to update every connected session for the user.
     */
    List<ParticipationLog> findActiveByUserId(UUID userId);

    /**
     * Returns all active sessions for a registered user within a specific meeting.
     * Used by the host kick flow to remove all devices of a user at once.
     */
    List<ParticipationLog> findActiveByMeetingIdAndUserId(UUID meetingId, UUID userId);

    /**
     * Returns all active sessions for a guest (identified by display name) within a specific meeting.
     * Used by the host kick flow to remove all sessions matching the guest's display name.
     */
    List<ParticipationLog> findActiveByMeetingIdAndDisplayName(UUID meetingId, String displayName);

    /** Returns read-only participant summaries for a meeting ordered by newest join first. */
    List<ParticipantSummary> findParticipantSummariesByMeetingId(UUID meetingId);

    boolean existsByMeetingIdAndUserId(UUID meetingId, UUID userId);

    List<ParticipantSummary> findDistinctParticipantSummariesByMeetingId(UUID meetingId);
}
