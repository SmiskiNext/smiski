package io.github.smiskinext.meet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meet.domain.event.MeetingInvitationsSentEvent;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.valueobject.*;
import io.github.smiskinext.shared.domain.DomainEvent;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.util.List;
import org.junit.jupiter.api.Test;

class MeetingRecordInvitationsSentTest {

    @Test
    void registersEventWithCorrectInviteesAndEmbeddedTokens() {
        Meeting meeting = Meeting.instant(
                TenantId.of("tenant-1"),
                AccountId.of("host-1"),
                MeetingTitle.of("Test Meeting"),
                "Test description",
                JiraIssueLink.of("ISS-1", "PROJ-1", "PROJ"),
                MeetingSettings.defaults(),
                MeetingTimeZone.of("UTC"),
                ShortCode.of("ABC123DEF0"));
        meeting.start();
        meeting.clearDomainEvents();

        List<MeetingInvitationsSentEvent.InviteeInfo> invitees = List.of(
                new MeetingInvitationsSentEvent.InviteeInfo(
                        "acc-1", "a@test.com", "Alice", "raw-token-1"),
                new MeetingInvitationsSentEvent.InviteeInfo(
                        "acc-2", "b@test.com", "Bob", "raw-token-2"));

        meeting.recordInvitationsSent(invitees);

        List<DomainEvent> events = meeting.getDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(MeetingInvitationsSentEvent.class);

        MeetingInvitationsSentEvent event = (MeetingInvitationsSentEvent) events.getFirst();
        assertThat(event.meetingId()).isEqualTo(meeting.getId().value());
        assertThat(event.tenantId()).isEqualTo("tenant-1");
        assertThat(event.invitees()).hasSize(2);
        assertThat(event.invitees().get(0).token()).isEqualTo("raw-token-1");
        assertThat(event.invitees().get(1).token()).isEqualTo("raw-token-2");
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
                ShortCode.of("ABC123DEF0"));
        meeting.start();
        meeting.clearDomainEvents();

        meeting.recordInvitationsSent(List.of());

        assertThat(meeting.getDomainEvents()).isEmpty();
    }
}
