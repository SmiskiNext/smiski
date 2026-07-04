package io.github.smiskinext.notification.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.notification.domain.port.UserLookupPort;
import io.github.smiskinext.notification.infrastructure.messaging.InviteeRespondedMessage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InviteeRespondedEmailRendererTest {

    private final InviteeRespondedEmailRenderer renderer = new InviteeRespondedEmailRenderer();

    @Test
    void rendersAcceptedEmail() {
        UUID hostId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        InviteeRespondedMessage message = new InviteeRespondedMessage(
                UUID.randomUUID(),
                inviteeId,
                null,
                UUID.randomUUID(),
                hostId,
                "Planning Session",
                null,
                null,
                Instant.parse("2026-04-02T10:00:00Z"),
                Instant.parse("2026-04-02T10:00:00Z"),
                null,
                null);

        InviteeRespondedEmailRenderer.RenderedEmail email = renderer.render(
                message,
                new UserLookupPort.UserInfo(hostId, "host@example.com", "Host"),
                new UserLookupPort.UserInfo(inviteeId, "alice@example.com", "Alice"));

        assertThat(email.subject())
                .isEqualTo("Alice accepted your meeting invitation for Planning Session");
        assertThat(email.html()).contains("Planning Session", "accepted");
    }

    @Test
    void rendersDeclinedEmail() {
        UUID hostId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        InviteeRespondedMessage message = new InviteeRespondedMessage(
                UUID.randomUUID(),
                inviteeId,
                null,
                UUID.randomUUID(),
                hostId,
                "Planning Session",
                null,
                null,
                Instant.parse("2026-04-02T10:00:00Z"),
                null,
                Instant.parse("2026-04-02T10:00:00Z"),
                null);

        InviteeRespondedEmailRenderer.RenderedEmail email = renderer.render(
                message,
                new UserLookupPort.UserInfo(hostId, "host@example.com", "Host"),
                new UserLookupPort.UserInfo(inviteeId, "bob@example.com", "Bob"));

        assertThat(email.subject())
                .isEqualTo("Bob declined your meeting invitation for Planning Session");
        assertThat(email.html()).contains("Planning Session", "declined");
    }
}
