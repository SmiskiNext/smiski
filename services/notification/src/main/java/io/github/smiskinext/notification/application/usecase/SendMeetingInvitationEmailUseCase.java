package io.github.smiskinext.notification.application.usecase;

import io.github.smiskinext.notification.domain.port.EmailSender;
import io.github.smiskinext.notification.infrastructure.email.MeetingInvitationEmailRenderer;
import io.github.smiskinext.notification.infrastructure.email.MeetingInvitationLinkFactory;
import io.github.smiskinext.notification.infrastructure.messaging.MeetingInvitationsSentMessage;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SendMeetingInvitationEmailUseCase {

    private final MeetingInvitationLinkFactory meetingInvitationLinkFactory;
    private final MeetingInvitationEmailRenderer meetingInvitationEmailRenderer;
    private final EmailSender emailSender;

    public SendMeetingInvitationEmailUseCase(
            MeetingInvitationLinkFactory meetingInvitationLinkFactory,
            MeetingInvitationEmailRenderer meetingInvitationEmailRenderer,
            EmailSender emailSender) {
        this.meetingInvitationLinkFactory = meetingInvitationLinkFactory;
        this.meetingInvitationEmailRenderer = meetingInvitationEmailRenderer;
        this.emailSender = emailSender;
    }

    /**
     * Sends an invitation email to a single invitee using the per-invitee invite token from
     * {@code invitation.inviteeTokens()}.
     */
    public void send(
            MeetingInvitationsSentMessage invitation,
            MeetingInvitationsSentMessage.InviteeInfo invitee) {
        String joinLink = resolveJoinLink(invitation, invitee);
        MeetingInvitationEmailRenderer.RenderedEmail renderedEmail =
                meetingInvitationEmailRenderer.render(invitation, invitee, joinLink);
        emailSender.send(invitee.email(), renderedEmail.subject(), renderedEmail.html());
    }

    private String resolveJoinLink(
            MeetingInvitationsSentMessage invitation,
            MeetingInvitationsSentMessage.InviteeInfo invitee) {
        Map<UUID, String> inviteeTokens = invitation.inviteeTokens();
        if (inviteeTokens != null && invitee.userId() != null) {
            String token = inviteeTokens.get(invitee.userId());
            if (token != null && !token.isBlank()) {
                return meetingInvitationLinkFactory.buildInviteLink(token);
            }
        }
        throw new IllegalStateException("No invite token found for invitee " + invitee.email()
                + " in meeting " + invitation.aggregateId()
                + ". All invitations must include per-invitee tokens.");
    }
}
