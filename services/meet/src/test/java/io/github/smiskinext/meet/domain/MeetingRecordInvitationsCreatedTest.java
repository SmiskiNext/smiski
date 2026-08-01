package io.github.smiskinext.meet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meet.domain.event.MeetingInvitationsCreatedEvent;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.valueobject.*;
import io.github.smiskinext.shared.domain.DomainEvent;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class MeetingRecordInvitationsCreatedTest {

    @Test
    void registersEventWithCorrectInvitees() {
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
                ShortCode.of("ABC123DEF0"),
                Duration.ofHours(1));
        meeting.start();
        meeting.clearDomainEvents();

        List<MeetingInvitationsCreatedEvent.InviteeInfo> invitees = List.of(
                new MeetingInvitationsCreatedEvent.InviteeInfo(
                        java.util.UUID.randomUUID(),
                        "acc-1",
                        "a@test.com",
                        "Alice",
                        "NEEDS_ACTION"),
                new MeetingInvitationsCreatedEvent.InviteeInfo(
                        java.util.UUID.randomUUID(), "acc-2", "b@test.com", "Bob", "NEEDS_ACTION"));

        meeting.recordInvitationsSent(invitees);

        List<DomainEvent> events = meeting.getDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(MeetingInvitationsCreatedEvent.class);

        MeetingInvitationsCreatedEvent event = (MeetingInvitationsCreatedEvent) events.getFirst();
        assertThat(event.meetingId()).isEqualTo(meeting.getId().value());
        assertThat(event.tenantId()).isEqualTo("tenant-1");
        assertThat(event.startTime()).isNotNull();
        assertThat(event.endTime()).isNotNull();
        assertThat(event.invitees()).hasSize(2);
        assertThat(event.invitees().get(0).accountId()).isEqualTo("acc-1");
        assertThat(event.invitees().get(1).accountId()).isEqualTo("acc-2");
    }

    @Test
    void doesNotRegisterEventWhenInviteeListIsEmpty() {
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
                ShortCode.of("ABC123DEF0"),
                Duration.ofHours(1));
        meeting.start();
        meeting.clearDomainEvents();

        meeting.recordInvitationsSent(List.of());

        assertThat(meeting.getDomainEvents()).isEmpty();
    }
}
