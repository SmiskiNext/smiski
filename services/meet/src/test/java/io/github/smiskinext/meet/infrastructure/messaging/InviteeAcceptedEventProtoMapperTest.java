package io.github.smiskinext.meet.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Message;
import io.github.smiskinext.event.meet.v1.InviteeAccepted;
import io.github.smiskinext.meet.domain.event.InviteeAcceptedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InviteeAcceptedEventProtoMapperTest {

    private final InviteeAcceptedEventProtoMapper mapper = new InviteeAcceptedEventProtoMapper();

    @Test
    void mapsIssueKeyAndShortCodeToProto() {
        InviteeAcceptedEvent event = new InviteeAcceptedEvent(
                UUID.randomUUID(),
                "tenant-1",
                UUID.randomUUID(),
                "inviter-1",
                UUID.randomUUID(),
                "bob@test.com",
                "ACCEPTED",
                Instant.now(),
                "Sprint Planning",
                Instant.parse("2026-01-15T10:00:00Z"),
                Instant.parse("2026-01-15T11:00:00Z"),
                "UTC",
                "host@example.com",
                "Host",
                "Bob",
                "uid-cal-1",
                1,
                "issue-1",
                "PROJ-1",
                "PROJ",
                "ABC123");

        Message result = mapper.toProto(event);

        assertThat(result).isInstanceOf(InviteeAccepted.class);
        InviteeAccepted proto = (InviteeAccepted) result;
        assertThat(proto.getIssueId()).isEqualTo("issue-1");
        assertThat(proto.getIssueKey()).isEqualTo("PROJ-1");
        assertThat(proto.getProjectKey()).isEqualTo("PROJ");
        assertThat(proto.getShortCode()).isEqualTo("ABC123");
    }
}
