package io.github.smiskinext.notification.application.usecase;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.notification.domain.port.EmailSender;
import io.github.smiskinext.notification.infrastructure.email.PasswordResetEmailRenderer;
import io.github.smiskinext.notification.infrastructure.messaging.PasswordResetRequestedMessage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SendPasswordResetEmailUseCaseTest {

    @Mock
    private PasswordResetEmailRenderer renderer;

    @Mock
    private EmailSender emailSender;

    private SendPasswordResetEmailUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SendPasswordResetEmailUseCase(renderer, emailSender);
    }

    @Test
    void orchestratesRenderingAndSending() {
        PasswordResetRequestedMessage message = new PasswordResetRequestedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "alice@example.com",
                "Alice Smith",
                "123456",
                Instant.now().plusSeconds(900),
                Instant.now());

        when(renderer.render(message))
                .thenReturn(new PasswordResetEmailRenderer.RenderedEmail(
                        "Your Password Reset Code", "<html>OTP: 123456</html>"));

        useCase.send(message);

        verify(renderer).render(message);
        verify(emailSender)
                .send("alice@example.com", "Your Password Reset Code", "<html>OTP: 123456</html>");
    }

    @Test
    void sendsEmailToCorrectRecipient() {
        PasswordResetRequestedMessage message = new PasswordResetRequestedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "bob@example.com",
                "Bob Jones",
                "654321",
                Instant.now().plusSeconds(900),
                Instant.now());

        when(renderer.render(message))
                .thenReturn(
                        new PasswordResetEmailRenderer.RenderedEmail("Subject", "<html></html>"));

        useCase.send(message);

        verify(emailSender).send("bob@example.com", "Subject", "<html></html>");
    }
}
