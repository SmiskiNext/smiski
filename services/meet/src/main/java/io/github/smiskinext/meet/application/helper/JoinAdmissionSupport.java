package io.github.smiskinext.meet.application.helper;

import io.github.smiskinext.meet.domain.model.InviteeStatus;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingInvitee;

import java.util.Optional;

/**
 * Decides immediate admission eligibility under MANUAL_APPROVAL admission.
 *
 * <p>A caller bypasses the pending-request queue when they are the meeting host or an active
 * invitee whose RSVP status is ACCEPTED or TENTATIVE. The predicate operates on already-loaded
 * state and does not perform repository lookups itself.
 */
public final class JoinAdmissionSupport {

    private JoinAdmissionSupport() {}

    /**
     * Determines whether the caller is eligible for immediate admission.
     *
     * @param meeting the target meeting
     * @param accountId the joining caller's account identifier
     * @param invitee the invitee record for the caller, when present
     * @return {@code true} when the caller is the host or an ACCEPTED/TENTATIVE invitee
     */
    public static boolean isEligibleForImmediateAdmission(
            Meeting meeting, String accountId, Optional<MeetingInvitee> invitee) {
        if (meeting.getHostId().value().equals(accountId)) {
            return true;
        }
        return invitee.map(MeetingInvitee::getStatus)
                .filter(status ->
                        status == InviteeStatus.ACCEPTED || status == InviteeStatus.TENTATIVE)
                .isPresent();
    }
}
