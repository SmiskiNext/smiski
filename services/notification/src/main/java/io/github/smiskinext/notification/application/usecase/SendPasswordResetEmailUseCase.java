package io.github.smiskinext.notification.application.usecase;

import io.github.smiskinext.notification.domain.port.EmailSender;
import io.github.smiskinext.notification.infrastructure.email.PasswordResetEmailRenderer;
import io.github.smiskinext.notification.infrastructure.messaging.PasswordResetRequestedMessage;

import org.springframework.stereotype.Service;

/**
 * Use case for sending password reset OTP emails.
 */
@Service
public class SendPasswordResetEmailUseCase {

    private final PasswordResetEmailRenderer passwordResetEmailRenderer;
    private final EmailSender emailSender;

    public SendPasswordResetEmailUseCase(
            PasswordResetEmailRenderer passwordResetEmailRenderer, EmailSender emailSender) {
        this.passwordResetEmailRenderer = passwordResetEmailRenderer;
        this.emailSender = emailSender;
    }

    public void send(PasswordResetRequestedMessage message) {
        PasswordResetEmailRenderer.RenderedEmail renderedEmail =
                passwordResetEmailRenderer.render(message);
        emailSender.send(message.email(), renderedEmail.subject(), renderedEmail.html());
    }
}
