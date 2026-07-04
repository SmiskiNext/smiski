package io.github.smiskinext.meetingmanagement.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeetingInviteTokensInvalidatedEventTest {

    @Test
    void eventHasCorrectTopicAndType() {
        MeetingInviteTokensInvalidatedEvent event = sampleEvent();

        assertThat(event.topic()).isEqualTo("meeting-management.meeting.invite-tokens.invalidated");
        assertThat(event.eventType())
                .isEqualTo("io.github.phunguy65.zms.meeting.invite-tokens.invalidated.v1");
        assertThat(event.aggregateType()).isEqualTo("meeting");
    }

    @Test
    void occurredAtDelegatesToUpdatedAt() {
        Instant updatedAt = Instant.now();
        MeetingInviteTokensInvalidatedEvent event = new MeetingInviteTokensInvalidatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Test Meeting",
                "TESTCODE12",
                List.of(),
                updatedAt);

        assertThat(event.occurredAt()).isEqualTo(updatedAt);
    }

    @Test
    void aggregateIdIsTheMeetingId() {
        UUID meetingId = UUID.randomUUID();
        MeetingInviteTokensInvalidatedEvent event = new MeetingInviteTokensInvalidatedEvent(
                UUID.randomUUID(),
                meetingId,
                UUID.randomUUID(),
                "Test Meeting",
                "TESTCODE12",
                List.of(),
                Instant.now());

        assertThat(event.aggregateId()).isEqualTo(meetingId);
    }

    @Test
    void carriesAffectedInviteeDetails() {
        UUID inviteeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MeetingInviteTokensInvalidatedEvent.AffectedInviteeInfo inviteeInfo =
                new MeetingInviteTokensInvalidatedEvent.AffectedInviteeInfo(
                        inviteeId, userId, "alice@example.com", "Alice");
        MeetingInviteTokensInvalidatedEvent event = new MeetingInviteTokensInvalidatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Test Meeting",
                "TESTCODE12",
                List.of(inviteeInfo),
                Instant.now());

        assertThat(event.affectedInvitees()).hasSize(1);
        MeetingInviteTokensInvalidatedEvent.AffectedInviteeInfo actual =
                event.affectedInvitees().getFirst();
        assertThat(actual.inviteeId()).isEqualTo(inviteeId);
        assertThat(actual.userId()).isEqualTo(userId);
        assertThat(actual.email()).isEqualTo("alice@example.com");
        assertThat(actual.displayName()).isEqualTo("Alice");
    }

    private static MeetingInviteTokensInvalidatedEvent sampleEvent() {
        return new MeetingInviteTokensInvalidatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Planning Session",
                "PLANCODE12",
                List.of(),
                Instant.now());
    }
}
