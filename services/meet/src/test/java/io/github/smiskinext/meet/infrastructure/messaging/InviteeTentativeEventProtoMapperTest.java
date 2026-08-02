package io.github.smiskinext.meet.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Message;
import io.github.smiskinext.event.meet.v1.InviteeTentative;
import io.github.smiskinext.meet.domain.event.InviteeTentativeEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InviteeTentativeEventProtoMapperTest {

    private final InviteeTentativeEventProtoMapper mapper = new InviteeTentativeEventProtoMapper();

    @Test
    void mapsIssueKeyAndShortCodeToProto() {
        InviteeTentativeEvent event = new InviteeTentativeEvent(
                UUID.randomUUID(),
                "tenant-1",
                UUID.randomUUID(),
                "inviter-1",
                UUID.randomUUID(),
                "dave@test.com",
                "TENTATIVE",
                Instant.now(),
                "Sprint Planning",
                Instant.parse("2026-01-15T10:00:00Z"),
                Instant.parse("2026-01-15T11:00:00Z"),
                "UTC",
                "host@example.com",
                "Host",
                "Dave",
                "uid-cal-1",
                3,
                "issue-3",
                "PROJ-3",
                "PROJ",
                "GHI789");

        Message result = mapper.toProto(event);

        assertThat(result).isInstanceOf(InviteeTentative.class);
        InviteeTentative proto = (InviteeTentative) result;
        assertThat(proto.getIssueId()).isEqualTo("issue-3");
        assertThat(proto.getIssueKey()).isEqualTo("PROJ-3");
        assertThat(proto.getProjectKey()).isEqualTo("PROJ");
        assertThat(proto.getShortCode()).isEqualTo("GHI789");
    }
}
