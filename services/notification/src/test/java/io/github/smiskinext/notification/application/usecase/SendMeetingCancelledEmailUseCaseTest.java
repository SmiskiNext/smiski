package io.github.smiskinext.notification.application.usecase;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.notification.domain.port.EmailSender;
import io.github.smiskinext.notification.infrastructure.email.MeetingCancelledEmailRenderer;
import io.github.smiskinext.notification.infrastructure.messaging.MeetingCancelledMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SendMeetingCancelledEmailUseCaseTest {

    @Mock
    private MeetingCancelledEmailRenderer renderer;

    @Mock
    private EmailSender emailSender;

    private SendMeetingCancelledEmailUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SendMeetingCancelledEmailUseCase(renderer, emailSender);
    }

    @Test
    void rendersAndSendsCancellationEmail() {
        MeetingCancelledMessage cancellation = new MeetingCancelledMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Planning Session",
                "ABC1234567",
                Instant.parse("2026-04-03T10:00:00Z"),
                List.of(new MeetingCancelledMessage.InviteeInfo(
                        UUID.randomUUID(),
                        "alice@example.com",
                        "Alice",
                        "ACCEPTED",
                        Instant.parse("2026-04-02T09:00:00Z"))),
                Instant.parse("2026-04-02T11:00:00Z"));
        MeetingCancelledMessage.InviteeInfo invitee = cancellation.invitees().getFirst();

        when(renderer.render(cancellation, invitee))
                .thenReturn(new MeetingCancelledEmailRenderer.RenderedEmail(
                        "Meeting Cancelled", "<p>Cancelled</p>"));

        useCase.send(cancellation, invitee);

        verify(renderer).render(cancellation, invitee);
        verify(emailSender).send("alice@example.com", "Meeting Cancelled", "<p>Cancelled</p>");
    }
}
