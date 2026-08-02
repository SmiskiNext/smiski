package io.github.smiskinext.notification.infrastructure.email;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.Attachment;
import com.resend.services.emails.model.CreateEmailOptions;
import io.github.smiskinext.notification.domain.model.CalendarEmail;
import io.github.smiskinext.notification.domain.port.EmailSender;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public ResendEmailSender(EmailProperties properties) {
        this(new Resend(properties.getApiKey()), properties.getSender());
    }

    ResendEmailSender(Resend resend, String sender) {
        this.resend = resend;
        this.sender = sender;
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

        CreateEmailOptions.Builder optionsBuilder = CreateEmailOptions.builder()
                .from(sender)
                .to(email.recipient())
                .subject(email.subject())
                .text(email.body())
                .attachments(attachment);

        if (email.htmlBody() != null) {
            optionsBuilder.html(email.htmlBody());
        }

        try {
            resend.emails().send(optionsBuilder.build());
        } catch (ResendException e) {
            throw new EmailDeliveryException(
                    "Failed to send calendar email to " + email.recipient(), e);
        }
    }
}
