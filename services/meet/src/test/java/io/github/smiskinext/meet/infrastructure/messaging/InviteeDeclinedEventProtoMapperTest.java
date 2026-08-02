package io.github.smiskinext.meet.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Message;
import io.github.smiskinext.event.meet.v1.InviteeDeclined;
import io.github.smiskinext.meet.domain.event.InviteeDeclinedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InviteeDeclinedEventProtoMapperTest {

    private final InviteeDeclinedEventProtoMapper mapper = new InviteeDeclinedEventProtoMapper();

    @Test
    void mapsIssueKeyAndShortCodeToProto() {
        InviteeDeclinedEvent event = new InviteeDeclinedEvent(
                UUID.randomUUID(),
                "tenant-1",
                UUID.randomUUID(),
                "inviter-1",
                UUID.randomUUID(),
                "carol@test.com",
                "DECLINED",
                Instant.now(),
                "Sprint Planning",
                Instant.parse("2026-01-15T10:00:00Z"),
                Instant.parse("2026-01-15T11:00:00Z"),
                "UTC",
                "host@example.com",
                "Host",
                "Carol",
                "uid-cal-1",
                2,
                "issue-2",
                "PROJ-2",
                "PROJ",
                "DEF456");

        Message result = mapper.toProto(event);

        assertThat(result).isInstanceOf(InviteeDeclined.class);
        InviteeDeclined proto = (InviteeDeclined) result;
        assertThat(proto.getIssueId()).isEqualTo("issue-2");
        assertThat(proto.getIssueKey()).isEqualTo("PROJ-2");
        assertThat(proto.getProjectKey()).isEqualTo("PROJ");
        assertThat(proto.getShortCode()).isEqualTo("DEF456");
    }
}
