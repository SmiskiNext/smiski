package io.github.smiskinext.meet.domain.port;

import io.github.smiskinext.meet.domain.model.MeetingInvitee;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.Email;
import io.github.smiskinext.meet.domain.model.valueobject.InviteeId;
import io.github.smiskinext.meet.domain.projection.InviteeSummary;

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
     * Returns an active (non-removed) invitee by meeting and registered account identity.
     */
    Optional<MeetingInvitee> findByMeetingIdAndAccountId(UUID meetingId, AccountId accountId);

    /**
     * Returns an active (non-removed) invitee by meeting and email address.
     * Used for inbound email reply resolution where account identity is unavailable.
     */
    Optional<MeetingInvitee> findByMeetingIdAndEmail(UUID meetingId, Email email);

    /**
     * Returns NEEDS_ACTION invitees for the given registered account.
     */
    List<MeetingInvitee> findPendingByAccountId(AccountId accountId);

    /**
     * Returns the count of active (NEEDS_ACTION, ACCEPTED or TENTATIVE) invitees for the
     * given meeting.
     */
    long countActiveByMeetingId(UUID meetingId);

    List<InviteeSummary> findSummariesByMeetingId(UUID meetingId);
}
