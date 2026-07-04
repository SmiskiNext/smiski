package io.github.smiskinext.meetingmanagement.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeetingInvitationsSentEventTest {

    @Test
    void toStringExposesShortCodeAndTitle() {
        MeetingInvitationsSentEvent event = new MeetingInvitationsSentEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Planning Session",
                "ABC1234567",
                Instant.parse("2026-04-02T10:15:30Z"),
                List.of(new MeetingInvitationsSentEvent.InviteeInfo(
                        UUID.randomUUID(), "alice@example.com", "Alice")),
                Map.of(),
                Instant.parse("2026-04-02T09:00:00Z"));

        String str = event.toString();
        assertThat(str)
                .contains("meetingShortCode=ABC1234567")
                .contains("Planning Session")
                .doesNotContain("rawPassword");
    }

    @Test
    void toStringHandlesNullableFields() {
        MeetingInvitationsSentEvent event = new MeetingInvitationsSentEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "XYZ7654321",
                null,
                List.of(new MeetingInvitationsSentEvent.InviteeInfo(
                        null, "guest@example.com", null)),
                Map.of(),
                Instant.parse("2026-04-02T09:00:00Z"));

        String str = event.toString();
        assertThat(str)
                .contains("meetingTitle=null")
                .contains("startTime=null")
                .contains("inviteeTokenCount=0");
    }

    @Test
    void eventTopicAndTypeAreCorrect() {
        MeetingInvitationsSentEvent event = new MeetingInvitationsSentEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Test Meeting",
                "TESTCODE12",
                Instant.now(),
                List.of(),
                Map.of(),
                Instant.now());

        assertThat(event.topic()).isEqualTo("meeting-management.meeting.invitations.sent");
        assertThat(event.eventType())
                .isEqualTo("io.github.phunguy65.zms.meeting.invitations.sent.v1");
        assertThat(event.aggregateType()).isEqualTo("meeting");
    }
}
