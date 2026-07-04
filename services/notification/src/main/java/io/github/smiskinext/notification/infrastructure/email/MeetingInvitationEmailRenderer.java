package io.github.smiskinext.notification.infrastructure.email;

import io.github.smiskinext.notification.infrastructure.messaging.MeetingInvitationsSentMessage;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class MeetingInvitationEmailRenderer {

    private static final DateTimeFormatter START_TIME_FORMATTER = DateTimeFormatter.ofPattern(
                    "EEE, dd MMM yyyy 'at' HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    public RenderedEmail render(
            MeetingInvitationsSentMessage invitation,
            MeetingInvitationsSentMessage.InviteeInfo invitee,
            String joinLink) {
        String safeDisplayName = HtmlUtils.htmlEscape(displayName(invitee));
        String safeMeetingTitle = HtmlUtils.htmlEscape(meetingTitle(invitation));
        String safeStartTime = HtmlUtils.htmlEscape(startTime(invitation.startTime()));
        String safeJoinLink = HtmlUtils.htmlEscape(joinLink);
        String subject = "Invitation: " + sanitizeSubject(meetingTitle(invitation));

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
                                <h1 style="margin:0 0 16px;font-size:28px;line-height:1.2;">You're invited to %s</h1>
                                <p style="margin:0 0 8px;font-size:16px;line-height:1.6;">Start time: %s</p>
                                <p style="margin:0 0 24px;font-size:16px;line-height:1.6;">Use the button below to join the meeting.</p>
                                <table role="presentation" cellspacing="0" cellpadding="0" style="margin:0 0 24px;">
                                  <tr>
                                    <td bgcolor="#2563eb" style="border-radius:999px;">
                                      <a href="%s" style="display:inline-block;padding:14px 24px;color:#ffffff;text-decoration:none;font-weight:700;">Join Meeting</a>
                                    </td>
                                  </tr>
                                </table>
                                <p style="margin:0 0 8px;font-size:14px;line-height:1.6;color:#475569;">If the button doesn't work, open this link:</p>
                                <p style="margin:0;font-size:14px;line-height:1.6;word-break:break-all;color:#2563eb;">%s</p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(
                safeDisplayName, safeMeetingTitle, safeStartTime, safeJoinLink, safeJoinLink);
        return new RenderedEmail(subject, html);
    }

    private String displayName(MeetingInvitationsSentMessage.InviteeInfo invitee) {
        return invitee.displayName() != null && !invitee.displayName().isBlank()
                ? invitee.displayName()
                : "there";
    }

    private String meetingTitle(MeetingInvitationsSentMessage invitation) {
        return invitation.meetingTitle() != null && !invitation.meetingTitle().isBlank()
                ? invitation.meetingTitle()
                : "your Zero Meeting System meeting";
    }

    private String startTime(Instant startTime) {
        return startTime != null
                ? START_TIME_FORMATTER.format(startTime)
                : "The host will start the meeting soon.";
    }

    private String sanitizeSubject(String value) {
        return value.replace("\r", " ").replace("\n", " ").trim();
    }

    public record RenderedEmail(String subject, String html) {}
}
