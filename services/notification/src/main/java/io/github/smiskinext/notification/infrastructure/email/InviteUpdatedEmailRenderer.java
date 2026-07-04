package io.github.smiskinext.notification.infrastructure.email;

import io.github.smiskinext.notification.infrastructure.messaging.MeetingInviteTokensInvalidatedMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders the HTML email body and subject for "invite link updated" notifications.
 *
 * <p>Sent when a host changes the meeting password on a SCHEDULED meeting, invalidating all
 * existing invite tokens. The email instructs the invitee to use their updated invite link from a
 * re-sent invitation email.
 */
@Component
public class InviteUpdatedEmailRenderer {

    public RenderedEmail render(
            MeetingInviteTokensInvalidatedMessage event,
            MeetingInviteTokensInvalidatedMessage.AffectedInviteeInfo invitee) {
        String safeDisplayName = HtmlUtils.htmlEscape(displayName(invitee));
        String safeMeetingTitle = HtmlUtils.htmlEscape(meetingTitle(event));
        String subject = "Update: Your meeting invite for " + sanitizeSubject(meetingTitle(event))
                + " has been updated";

        String html = """
                <html>
                  <body style="margin:0;padding:24px;background:#f5f7fb;font-family:Arial,sans-serif;color:#14213d;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                      <tr>
                        <td align="center">
                          <table role="presentation" width="600" cellspacing="0" cellpadding="0" style="background:#ffffff;border-radius:16px;padding:32px;">
                            <tr>
                              <td>
                                <p style="margin:0 0 12px;font-size:16px;">Hello %s,</p>
                                <h1 style="margin:0 0 16px;font-size:28px;line-height:1.2;">Your invite for %s has been updated</h1>
                                <p style="margin:0 0 16px;font-size:16px;line-height:1.6;">The meeting host has updated the meeting security settings. Your previous invite link is no longer valid.</p>
                                <p style="margin:0;font-size:16px;line-height:1.6;">A new invite link will be sent to you shortly. Please use the latest invitation email to join the meeting.</p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(safeDisplayName, safeMeetingTitle);
        return new RenderedEmail(subject, html);
    }

    private String displayName(MeetingInviteTokensInvalidatedMessage.AffectedInviteeInfo invitee) {
        return invitee.displayName() != null && !invitee.displayName().isBlank()
                ? invitee.displayName()
                : "there";
    }

    private String meetingTitle(MeetingInviteTokensInvalidatedMessage event) {
        return event.meetingTitle() != null && !event.meetingTitle().isBlank()
                ? event.meetingTitle()
                : "your Zero Meeting System meeting";
    }

    private String sanitizeSubject(String value) {
        return value.replace("\r", " ").replace("\n", " ").trim();
    }

    public record RenderedEmail(String subject, String html) {}
}
