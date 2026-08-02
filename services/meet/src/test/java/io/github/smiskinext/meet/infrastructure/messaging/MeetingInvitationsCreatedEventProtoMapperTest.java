package io.github.smiskinext.meet.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Message;
import io.github.smiskinext.event.meet.v1.MeetingInvitationsCreated;
import io.github.smiskinext.meet.domain.event.MeetingInvitationsCreatedEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeetingInvitationsCreatedEventProtoMapperTest {

    private final MeetingInvitationsCreatedEventProtoMapper mapper =
            new MeetingInvitationsCreatedEventProtoMapper();

    @Test
    void mapsIssueFieldsToProto() {
        MeetingInvitationsCreatedEvent event = new MeetingInvitationsCreatedEvent(
                UUID.randomUUID(),
                "tenant-1",
                UUID.randomUUID(),
                "Sprint Planning",
                "ABC123",
                Instant.parse("2026-01-15T10:00:00Z"),
                Instant.parse("2026-01-15T11:00:00Z"),
                "UTC",
                "host@example.com",
                "Host User",
                "uid-cal-1",
                1,
                List.of(new MeetingInvitationsCreatedEvent.InviteeInfo(
                        UUID.randomUUID(), "acc-1", "bob@test.com", "Bob", "NEEDS_ACTION")),
                "issue-42",
                "PROJ-42",
                "PROJ",
                Instant.now());

        Message result = mapper.toProto(event);

        assertThat(result).isInstanceOf(MeetingInvitationsCreated.class);
        MeetingInvitationsCreated proto = (MeetingInvitationsCreated) result;
        assertThat(proto.getIssueId()).isEqualTo("issue-42");
        assertThat(proto.getIssueKey()).isEqualTo("PROJ-42");
        assertThat(proto.getProjectKey()).isEqualTo("PROJ");
        assertThat(proto.getMeetingShortCode()).isEqualTo("ABC123");
    }

    @Test
    void mapsEmptyIssueFieldsWithoutError() {
        MeetingInvitationsCreatedEvent event = new MeetingInvitationsCreatedEvent(
                UUID.randomUUID(),
                "tenant-1",
                UUID.randomUUID(),
                "Meeting",
                "XYZ789",
                null,
                null,
                "UTC",
                "host@example.com",
                "Host User",
                "uid-cal-2",
                0,
                List.of(new MeetingInvitationsCreatedEvent.InviteeInfo(
                        UUID.randomUUID(), "acc-2", "carol@test.com", "Carol", "NEEDS_ACTION")),
                "",
                "",
                "",
                Instant.now());

        Message result = mapper.toProto(event);

        MeetingInvitationsCreated proto = (MeetingInvitationsCreated) result;
        assertThat(proto.getIssueId()).isEmpty();
        assertThat(proto.getIssueKey()).isEmpty();
        assertThat(proto.getProjectKey()).isEmpty();
    }
}
