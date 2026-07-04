package io.github.smiskinext.notification.infrastructure.email;

import io.github.smiskinext.notification.infrastructure.messaging.MeetingCancelledMessage;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class MeetingCancelledEmailRenderer {

    private static final DateTimeFormatter START_TIME_FORMATTER = DateTimeFormatter.ofPattern(
                    "EEE, dd MMM yyyy 'at' HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    public RenderedEmail render(
            MeetingCancelledMessage cancellation, MeetingCancelledMessage.InviteeInfo invitee) {
        String safeDisplayName = HtmlUtils.htmlEscape(displayName(invitee));
        String safeMeetingTitle = HtmlUtils.htmlEscape(meetingTitle(cancellation));
        String safeStartTime = HtmlUtils.htmlEscape(startTime(cancellation.startTime()));
        String subject = "Meeting Cancelled: " + sanitizeSubject(meetingTitle(cancellation));

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
                                <h1 style="margin:0 0 16px;font-size:28px;line-height:1.2;">%s has been cancelled</h1>
                                <p style="margin:0 0 8px;font-size:16px;line-height:1.6;">Scheduled start time: %s</p>
                                <p style="margin:0;font-size:16px;line-height:1.6;">The host has cancelled this meeting. You do not need to take any further action.</p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(safeDisplayName, safeMeetingTitle, safeStartTime);
        return new RenderedEmail(subject, html);
    }

    private String displayName(MeetingCancelledMessage.InviteeInfo invitee) {
        return invitee.displayName() != null && !invitee.displayName().isBlank()
                ? invitee.displayName()
                : "there";
    }

    private String meetingTitle(MeetingCancelledMessage cancellation) {
        return cancellation.meetingTitle() != null
                        && !cancellation.meetingTitle().isBlank()
                ? cancellation.meetingTitle()
                : "your Zero Meeting System meeting";
    }

    private String startTime(Instant startTime) {
        return startTime != null
                ? START_TIME_FORMATTER.format(startTime)
                : "The host had not published a scheduled start time.";
    }

    private String sanitizeSubject(String value) {
        return value.replaceAll("\\p{Cntrl}", " ").trim().replaceAll("\\s+", " ");
    }

    public record RenderedEmail(String subject, String html) {}
}
