package io.github.smiskinext.notification.infrastructure.email;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;

import io.github.smiskinext.notification.domain.port.EmailSender;
import io.github.smiskinext.notification.infrastructure.config.NotificationProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ResendEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

    private final Resend resend;
    private final NotificationProperties notificationProperties;

    public ResendEmailSender(Resend resend, NotificationProperties notificationProperties) {
        this.resend = resend;
        this.notificationProperties = notificationProperties;
    }

    @Override
    public void send(String toEmail, String subject, String html) {
        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(fromAddress())
                .to(toEmail)
                .subject(subject)
                .html(html)
                .build();
        try {
            var response = resend.emails().send(options);
            log.info("Sent invitation email via Resend id={} to {}", response.getId(), toEmail);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to send invitation email via Resend", exception);
        }
    }

    private String fromAddress() {
        return notificationProperties.getResend().getFromName()
                + " <"
                + notificationProperties.getResend().getFromEmail()
                + ">";
    }
}
