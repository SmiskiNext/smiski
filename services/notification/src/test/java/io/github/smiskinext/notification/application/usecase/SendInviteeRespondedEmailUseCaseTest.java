package io.github.smiskinext.notification.application.usecase;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.notification.domain.port.EmailSender;
import io.github.smiskinext.notification.domain.port.UserLookupPort;
import io.github.smiskinext.notification.infrastructure.email.InviteeRespondedEmailRenderer;
import io.github.smiskinext.notification.infrastructure.messaging.InviteeRespondedMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SendInviteeRespondedEmailUseCaseTest {

    @Mock
    private UserLookupPort userLookupPort;

    @Mock
    private InviteeRespondedEmailRenderer renderer;

    @Mock
    private EmailSender emailSender;

    private SendInviteeRespondedEmailUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SendInviteeRespondedEmailUseCase(userLookupPort, renderer, emailSender);
    }

    @Test
    void resolvesUsersAndSendsResponseEmailToHost() {
        UUID inviteeId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        InviteeRespondedMessage response = new InviteeRespondedMessage(
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
        UserLookupPort.UserInfo host =
                new UserLookupPort.UserInfo(hostId, "host@example.com", "Host");
        UserLookupPort.UserInfo invitee =
                new UserLookupPort.UserInfo(inviteeId, "alice@example.com", "Alice");
        when(userLookupPort.findUsersByIds(List.of(hostId, inviteeId)))
                .thenReturn(Map.of(hostId, host, inviteeId, invitee));
        when(renderer.render(response, host, invitee))
                .thenReturn(new InviteeRespondedEmailRenderer.RenderedEmail(
                        "Alice accepted your meeting invitation for Planning Session",
                        "<p>Alice accepted</p>"));

        useCase.send(response);

        verify(renderer).render(response, host, invitee);
        verify(emailSender)
                .send(
                        "host@example.com",
                        "Alice accepted your meeting invitation for Planning Session",
                        "<p>Alice accepted</p>");
    }
}
