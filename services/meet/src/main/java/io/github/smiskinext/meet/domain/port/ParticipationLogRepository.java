package io.github.smiskinext.meet.domain.port;

import io.github.smiskinext.meet.domain.model.ParticipationLog;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitParticipantSid;
import io.github.smiskinext.meet.domain.projection.ParticipantSummary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for participation session logs (append-only; one row per join session).
 *
 * <p><b>Rejoin / close-then-open contract.</b> The DB enforces at most one active
 * (left_at IS NULL) row per (tenant, meeting, livekit_identity) via
 * {@code uq_participation_active_identity}. When issuing a token the join flow MUST:
 * <ol>
 *   <li>look up the active session via {@link #findActiveByMeetingIdAndIdentity} and,
 *       if present (orphaned by a lost participant_left webhook), call
 *       {@code supersede(now)} and {@link #save} it before inserting the new session;</li>
 *   <li>on a unique-violation from a concurrent rejoin, close the existing active row
 *       then retry the insert exactly once (retry-on-conflict; no advisory lock).</li>
 * </ol>
 */
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
     * Returns all active (not yet left) participation logs for a registered account.
     * Used by the user-profile sync consumer to update every connected session for the account.
     */
    List<ParticipationLog> findActiveByAccountId(AccountId accountId);

    /**
     * Returns all active sessions for a registered account within a specific meeting.
     * Used by the host kick flow to remove all devices of an account at once.
     */
    List<ParticipationLog> findActiveByMeetingIdAndAccountId(UUID meetingId, AccountId accountId);

    /**
     * Returns all active sessions matching a display name within a specific meeting.
     * Used by the host kick flow to remove all sessions matching a display name.
     */
    List<ParticipationLog> findActiveByMeetingIdAndDisplayName(UUID meetingId, String displayName);

    /** Returns read-only participant summaries for a meeting ordered by newest join first. */
    List<ParticipantSummary> findParticipantSummariesByMeetingId(UUID meetingId);

    boolean existsByMeetingIdAndAccountId(UUID meetingId, AccountId accountId);

    List<ParticipantSummary> findDistinctParticipantSummariesByMeetingId(UUID meetingId);
}
