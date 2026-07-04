package io.github.smiskinext.meetingmanagement.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meetingmanagement.domain.model.AdmissionPolicy;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeetingDomainEventsTest {

    @Test
    void meetingSettingsUpdatedEvent_exposesExpectedMetadata() {
        var oldSettings = new MeetingSettings(
                AdmissionPolicy.MANUAL_APPROVAL, true, 50, true, true, true, true, null);
        var newSettings = new MeetingSettings(
                AdmissionPolicy.ALLOW_ALL, true, 50, false, false, false, false, null);
        var event = new MeetingSettingsUpdatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                MeetingStatus.SCHEDULED,
                oldSettings,
                newSettings,
                Instant.parse("2026-04-02T09:00:00Z"));

        assertThat(event.aggregateType()).isEqualTo("meeting");
        assertThat(event.eventType())
                .isEqualTo("io.github.phunguy65.zms.meeting.settings.updated.v1");
        assertThat(event.topic()).isEqualTo("meeting-management.meeting.settings.updated");
        assertThat(event.oldSettings()).isEqualTo(oldSettings);
        assertThat(event.newSettings()).isEqualTo(newSettings);
    }

    @Test
    void meetingSettingsUpdatedEvent_carriesBothSettingsSnapshots() {
        var oldSettings = new MeetingSettings(
                AdmissionPolicy.MANUAL_APPROVAL, false, 30, true, true, true, true, null);
        var newSettings = new MeetingSettings(
                AdmissionPolicy.MANUAL_APPROVAL, false, 30, true, false, false, true, null);
        var event = new MeetingSettingsUpdatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                MeetingStatus.LIVE,
                oldSettings,
                newSettings,
                Instant.parse("2026-04-19T10:00:00Z"));

        // Old settings reflect pre-update state
        assertThat(event.oldSettings().chatEnabled()).isTrue();
        assertThat(event.oldSettings().allowMicrophone()).isTrue();
        // New settings reflect post-update state
        assertThat(event.newSettings().chatEnabled()).isFalse();
        assertThat(event.newSettings().allowMicrophone()).isFalse();
    }

    @Test
    void participantJoinedEvent_exposesExpectedMetadata() {
        var event = new ParticipantJoinedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Alice",
                Instant.parse("2026-04-02T09:00:00Z"));

        assertThat(event.aggregateType()).isEqualTo("meeting");
        assertThat(event.eventType())
                .isEqualTo("io.github.phunguy65.zms.meeting.participant.joined.v1");
        assertThat(event.topic()).isEqualTo("meeting-management.participant.joined");
    }

    @Test
    void participantLeftEvent_exposesExpectedMetadata() {
        var event = new ParticipantLeftEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Bob",
                Instant.parse("2026-04-02T09:05:00Z"));

        assertThat(event.aggregateType()).isEqualTo("meeting");
        assertThat(event.eventType())
                .isEqualTo("io.github.phunguy65.zms.meeting.participant.left.v1");
        assertThat(event.topic()).isEqualTo("meeting-management.participant.left");
    }

    @Test
    void participantKickedEvent_exposesExpectedMetadata() {
        var event = new ParticipantKickedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Charlie",
                Instant.parse("2026-04-02T09:10:00Z"));

        assertThat(event.aggregateType()).isEqualTo("meeting");
        assertThat(event.eventType())
                .isEqualTo("io.github.phunguy65.zms.meeting.participant.kicked.v1");
        assertThat(event.topic()).isEqualTo("meeting-management.participant.kicked");
    }

    @Test
    void joinRequestCreatedEvent_exposesExpectedMetadata() {
        var event = new JoinRequestCreatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Guest",
                "device-1",
                Instant.parse("2026-04-02T09:15:00Z"));

        assertThat(event.aggregateType()).isEqualTo("meeting");
        assertThat(event.eventType())
                .isEqualTo("io.github.phunguy65.zms.meeting.join-request.created.v1");
        assertThat(event.topic()).isEqualTo("meeting-management.join-request.created");
    }

    @Test
    void joinRequestApprovedEvent_exposesExpectedMetadata() {
        var event = new JoinRequestApprovedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "livekit-token",
                "meeting-room",
                Instant.parse("2026-04-02T09:20:00Z"));

        assertThat(event.aggregateType()).isEqualTo("meeting");
        assertThat(event.eventType())
                .isEqualTo("io.github.phunguy65.zms.meeting.join-request.approved.v1");
        assertThat(event.topic()).isEqualTo("meeting-management.join-request.approved");
    }

    @Test
    void joinRequestDeniedEvent_exposesExpectedMetadata() {
        var event = new JoinRequestDeniedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-04-02T09:25:00Z"));

        assertThat(event.aggregateType()).isEqualTo("meeting");
        assertThat(event.eventType())
                .isEqualTo("io.github.phunguy65.zms.meeting.join-request.denied.v1");
        assertThat(event.topic()).isEqualTo("meeting-management.join-request.denied");
    }

    @Test
    void joinRequestExpiredEvent_exposesExpectedMetadata() {
        var event = new JoinRequestExpiredEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-04-02T09:30:00Z"));

        assertThat(event.aggregateType()).isEqualTo("meeting");
        assertThat(event.eventType())
                .isEqualTo("io.github.phunguy65.zms.meeting.join-request.expired.v1");
        assertThat(event.topic()).isEqualTo("meeting-management.join-request.expired");
    }

    @Test
    void meetingInvitationsSentEvent_exposesExpectedMetadata() {
        var event = new MeetingInvitationsSentEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Planning Session",
                "ABC1234567",
                Instant.parse("2026-04-02T10:15:30Z"),
                List.of(new MeetingInvitationsSentEvent.InviteeInfo(
                        UUID.randomUUID(), "alice@example.com", "Alice")),
                Map.of(),
                Instant.parse("2026-04-02T09:00:00Z"));

        assertThat(event.aggregateType()).isEqualTo("meeting");
        assertThat(event.eventType())
                .isEqualTo("io.github.phunguy65.zms.meeting.invitations.sent.v1");
        assertThat(event.topic()).isEqualTo("meeting-management.meeting.invitations.sent");
    }

    @Test
    void meetingCancelledEvent_exposesExpectedMetadata() {
        var event = new MeetingCancelledEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Planning Session",
                "ABC1234567",
                Instant.parse("2026-04-02T10:15:30Z"),
                List.of(new MeetingCancelledEvent.InviteeInfo(
                        UUID.randomUUID(),
                        "alice@example.com",
                        "Alice",
                        "ACCEPTED",
                        Instant.parse("2026-04-02T09:00:00Z"))),
                Instant.parse("2026-04-02T11:00:00Z"));

        assertThat(event.aggregateType()).isEqualTo("meeting");
        assertThat(event.eventType()).isEqualTo("io.github.phunguy65.zms.meeting.cancelled.v1");
        assertThat(event.topic()).isEqualTo("meeting-management.meeting.cancelled");
        assertThat(event.invitees()).singleElement().satisfies(invitee -> {
            assertThat(invitee.email()).isEqualTo("alice@example.com");
            assertThat(invitee.status()).isEqualTo("ACCEPTED");
        });
    }
}
