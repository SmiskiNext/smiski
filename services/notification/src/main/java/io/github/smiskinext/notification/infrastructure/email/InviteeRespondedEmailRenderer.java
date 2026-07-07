package io.github.smiskinext.notification.infrastructure.email;

import io.github.smiskinext.notification.domain.port.UserLookupPort;
import io.github.smiskinext.notification.infrastructure.messaging.InviteeRespondedMessage;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class InviteeRespondedEmailRenderer {

    public RenderedEmail render(
            InviteeRespondedMessage response,
            UserLookupPort.UserInfo host,
            UserLookupPort.UserInfo invitee) {
        String action = action(response.resolvedResponseType());
        String inviteeName = displayName(invitee.fullName(), response.inviteeDisplayName());
        String safeHostName = HtmlUtils.htmlEscape(displayName(host.fullName(), null));
        String safeInviteeName = HtmlUtils.htmlEscape(inviteeName);
        String safeMeetingTitle = HtmlUtils.htmlEscape(meetingTitle(response));
        String safeAction = HtmlUtils.htmlEscape(action);
        String subject = sanitizeSubject("%s %s your meeting invitation for %s"
                .formatted(inviteeName, action, meetingTitle(response)));

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
                                <h1 style="margin:0 0 16px;font-size:28px;line-height:1.2;">%s %s your meeting invitation</h1>
                                <p style="margin:0 0 8px;font-size:16px;line-height:1.6;">Meeting: %s</p>
                                <p style="margin:0;font-size:16px;line-height:1.6;">Response: %s</p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(
                        safeHostName, safeInviteeName, safeAction, safeMeetingTitle, safeAction);
        return new RenderedEmail(subject, html);
    }

    private String action(String responseType) {
        return "ACCEPTED".equalsIgnoreCase(responseType) ? "accepted" : "declined";
    }

    private String displayName(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return "there";
    }

    private String meetingTitle(InviteeRespondedMessage response) {
        return response.meetingTitle() != null && !response.meetingTitle().isBlank()
                ? response.meetingTitle()
                : "your Zero Meeting System meeting";
    }

    private String sanitizeSubject(String value) {
        return value.replaceAll("\\p{Cntrl}", " ").trim().replaceAll("\\s+", " ");
    }

    public record RenderedEmail(String subject, String html) {}
}
