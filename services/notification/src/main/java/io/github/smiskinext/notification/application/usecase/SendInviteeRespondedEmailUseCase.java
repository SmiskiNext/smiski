package io.github.smiskinext.notification.application.usecase;

import io.github.smiskinext.notification.domain.port.EmailSender;
import io.github.smiskinext.notification.domain.port.UserLookupPort;
import io.github.smiskinext.notification.infrastructure.email.InviteeRespondedEmailRenderer;
import io.github.smiskinext.notification.infrastructure.messaging.InviteeRespondedMessage;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SendInviteeRespondedEmailUseCase {

    private final UserLookupPort userLookupPort;
    private final InviteeRespondedEmailRenderer renderer;
    private final EmailSender emailSender;

    public SendInviteeRespondedEmailUseCase(
            UserLookupPort userLookupPort,
            InviteeRespondedEmailRenderer renderer,
            EmailSender emailSender) {
        this.userLookupPort = userLookupPort;
        this.renderer = renderer;
        this.emailSender = emailSender;
    }

    public void send(InviteeRespondedMessage response) {
        Map<java.util.UUID, UserLookupPort.UserInfo> users = userLookupPort.findUsersByIds(
                List.of(response.inviterId(), response.resolvedInviteeId()));
        UserLookupPort.UserInfo host = users.get(response.inviterId());
        UserLookupPort.UserInfo invitee = users.get(response.resolvedInviteeId());
        if (host == null) {
            throw new IllegalArgumentException("Host not found: " + response.inviterId());
        }
        if (invitee == null) {
            throw new IllegalArgumentException(
                    "Invitee not found: " + response.resolvedInviteeId());
        }
        InviteeRespondedEmailRenderer.RenderedEmail renderedEmail =
                renderer.render(response, host, invitee);
        emailSender.send(host.email(), renderedEmail.subject(), renderedEmail.html());
    }
}
