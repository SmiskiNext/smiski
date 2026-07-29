package io.github.smiskinext.meet.application.helper;

import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingContext;
import io.github.smiskinext.meet.domain.model.MeetingInvitee;
import io.github.smiskinext.meet.domain.model.valueobject.InviteeId;
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Shared orchestration for the invitee self-response use cases (accept/decline/tentative).
 *
 * <p>Centralizes loading the target invitee within its meeting and building the {@link
 * MeetingContext} snapshot embedded into the enriched response events.
 */
public final class InviteeResponseSupport {

    private InviteeResponseSupport() {}

    /**
     * Loads the invitee by id and verifies it belongs to the given meeting.
     *
     * @return the invitee, or {@code null} when it does not exist or belongs to another meeting
     */
    public static @Nullable MeetingInvitee loadInviteeOfMeeting(
            MeetingInviteeRepository repository, UUID meetingId, UUID inviteeId) {
        MeetingInvitee invitee = repository.findById(InviteeId.of(inviteeId)).orElse(null);
        if (invitee == null || !invitee.getMeetingId().value().equals(meetingId)) {
            return null;
        }
        return invitee;
    }

    /**
     * Builds the iCalendar reply context from the loaded meeting aggregate.
     */
    public static MeetingContext context(Meeting meeting) {
        return new MeetingContext(
                meeting.getTitle().value(),
                meeting.getStartTime().orElse(null),
                meeting.getEndTime().orElse(null),
                meeting.getTimeZone().value(),
                meeting.getOrganizerEmail().value(),
                meeting.getOrganizerDisplayName().value(),
                meeting.getCalendarUid(),
                meeting.getCalendarSequence());
    }
}
