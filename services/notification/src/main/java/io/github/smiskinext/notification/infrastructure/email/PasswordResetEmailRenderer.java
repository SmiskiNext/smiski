package io.github.smiskinext.notification.infrastructure.email;

import io.github.smiskinext.notification.infrastructure.messaging.PasswordResetRequestedMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders password reset OTP emails.
 */
@Component
public class PasswordResetEmailRenderer {

    private static final int OTP_VALIDITY_MINUTES = 15;

    public RenderedEmail render(PasswordResetRequestedMessage message) {
        String safeDisplayName = HtmlUtils.htmlEscape(displayName(message.fullName()));
        String safeOtp = HtmlUtils.htmlEscape(message.otp());
        String subject = "Your Password Reset Code";

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
                                <h1 style="margin:0 0 16px;font-size:28px;line-height:1.2;">Password Reset Request</h1>
                                <p style="margin:0 0 16px;font-size:16px;line-height:1.6;">
                                  We received a request to reset your password. Use the code below to complete the process:
                                </p>
                                <table role="presentation" cellspacing="0" cellpadding="0" style="margin:0 auto 24px;">
                                  <tr>
                                    <td style="background:#f1f5f9;border-radius:8px;padding:16px 32px;">
                                      <span style="font-size:32px;font-weight:700;letter-spacing:8px;color:#2563eb;">%s</span>
                                    </td>
                                  </tr>
                                </table>
                                <p style="margin:0 0 16px;font-size:14px;line-height:1.6;color:#475569;">
                                  This code will expire in <strong>%d minutes</strong>.
                                </p>
                                <p style="margin:0 0 8px;font-size:14px;line-height:1.6;color:#dc2626;">
                                  <strong>Important:</strong> Never share this code with anyone. Our team will never ask for your reset code.
                                </p>
                                <hr style="border:none;border-top:1px solid #e2e8f0;margin:24px 0;">
                                <p style="margin:0;font-size:12px;line-height:1.6;color:#94a3b8;">
                                  If you didn't request a password reset, you can safely ignore this email. Your password won't be changed.
                                </p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(safeDisplayName, safeOtp, OTP_VALIDITY_MINUTES);

        return new RenderedEmail(subject, html);
    }

    private String displayName(String fullName) {
        return fullName != null && !fullName.isBlank() ? fullName : "there";
    }

    public record RenderedEmail(String subject, String html) {}
}
