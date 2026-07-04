package io.github.smiskinext.meetingmanagement.domain.port;

import io.github.smiskinext.meetingmanagement.domain.model.MeetingInvitee;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteeId;
import io.github.smiskinext.meetingmanagement.domain.projection.InviteeSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and querying meeting invitees.
 */
public interface MeetingInviteeRepository {

    /**
     * Persists a batch of invitees.
     */
    List<MeetingInvitee> saveAll(List<MeetingInvitee> invitees);

    /**
     * Persists a single invitee (e.g. after accept/decline).
     */
    MeetingInvitee save(MeetingInvitee invitee);

    /**
     * Returns an invitee by its identity.
     */
    Optional<MeetingInvitee> findById(InviteeId id);

    /**
     * Returns all invitees for the given meeting.
     */
    List<MeetingInvitee> findByMeetingId(UUID meetingId);

    /**
     * Returns an invitee by meeting and registered user identity.
     */
    Optional<MeetingInvitee> findByMeetingIdAndUserId(UUID meetingId, UUID userId);

    /**
     * Returns pending invitees for the given registered user.
     */
    List<MeetingInvitee> findPendingByUserId(UUID userId);

    /**
     * Returns the count of active (PENDING or ACCEPTED) invitees for the given meeting.
     */
    long countActiveByMeetingId(UUID meetingId);

    List<InviteeSummary> findSummariesByMeetingId(UUID meetingId);
}
