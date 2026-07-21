package io.github.smiskinext.meet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meet.domain.event.MeetingInvitationsDeletedEvent;
import io.github.smiskinext.meet.domain.event.MeetingInvitationsUpdatedEvent;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.valueobject.*;
import io.github.smiskinext.shared.domain.DomainEvent;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeetingRecordInviteesChangeTest {

    @Test
    void recordInviteesUpdatedRegistersEventWithoutBumpingCalendarSequence() {
        Meeting meeting = meeting();
        int sequenceBefore = meeting.getCalendarSequence();

        meeting.recordInviteesUpdated(List.of(new MeetingInvitationsUpdatedEvent.InviteeInfo(
                UUID.randomUUID(), "acc-1", "a@test.com", "Alice", "NEEDS_ACTION")));

        List<DomainEvent> events = meeting.getDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(MeetingInvitationsUpdatedEvent.class);
        assertThat(meeting.getCalendarSequence()).isEqualTo(sequenceBefore);
    }

    @Test
    void recordInviteesRemovedRegistersEventWithoutBumpingCalendarSequence() {
        Meeting meeting = meeting();
        int sequenceBefore = meeting.getCalendarSequence();

        meeting.recordInviteesRemoved(List.of(new MeetingInvitationsDeletedEvent.InviteeInfo(
                UUID.randomUUID(), "acc-1", "a@test.com", "Alice", "NEEDS_ACTION")));

        List<DomainEvent> events = meeting.getDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(MeetingInvitationsDeletedEvent.class);
        assertThat(meeting.getCalendarSequence()).isEqualTo(sequenceBefore);
    }

    @Test
    void emptyGroupsRegisterNoEvent() {
        Meeting meeting = meeting();

        meeting.recordInviteesUpdated(List.of());
        meeting.recordInviteesRemoved(List.of());

        assertThat(meeting.getDomainEvents()).isEmpty();
    }

    private Meeting meeting() {
        Meeting meeting = Meeting.instant(
                TenantId.of("tenant-1"),
                AccountId.of("host-1"),
                MeetingTitle.of("Test Meeting"),
                "Test description",
                JiraIssueLink.of("ISS-1", "PROJ-1", "PROJ"),
                MeetingSettings.defaults(),
                MeetingTimeZone.of("UTC"),
                Email.of("host@test.com"),
                InviteeDisplayName.of("Host"),
                ShortCode.of("ABC123DEF0"));
        meeting.clearDomainEvents();
        return meeting;
    }
}
