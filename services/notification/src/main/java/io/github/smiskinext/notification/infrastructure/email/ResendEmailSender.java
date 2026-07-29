package io.github.smiskinext.notification.infrastructure.email;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.Attachment;
import com.resend.services.emails.model.CreateEmailOptions;
import io.github.smiskinext.notification.domain.model.CalendarEmail;
import io.github.smiskinext.notification.domain.port.EmailSender;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Sends calendar emails through the Resend provider.
 *
 * <p>The iCalendar payload is attached as a base64 {@code text/calendar} part carrying the calendar
 * {@code method} content-type parameter so mail clients treat it as an invite/reply. A delivery
 * failure is rethrown so the consumer's retry and dead-letter handling can engage.
 */
@Component
public class ResendEmailSender implements EmailSender {

    private final Resend resend;
    private final String sender;

    public ResendEmailSender(EmailProperties properties) {
        this.resend = new Resend(properties.getApiKey());
        this.sender = properties.getSender();
    }

    @Override
    public void send(CalendarEmail email) {
        String encoded = Base64.getEncoder()
                .encodeToString(email.icsContent().getBytes(StandardCharsets.UTF_8));
        Attachment attachment = Attachment.builder()
                .fileName(email.attachmentName())
                .content(encoded)
                .contentType("text/calendar; method=" + email.calendarMethod())
                .build();

        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(sender)
                .to(email.recipient())
                .subject(email.subject())
                .text(email.body())
                .attachments(attachment)
                .build();

        try {
            resend.emails().send(options);
        } catch (ResendException e) {
            throw new EmailDeliveryException(
                    "Failed to send calendar email to " + email.recipient(), e);
        }
    }
}
