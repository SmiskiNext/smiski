package io.github.smiskinext.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.github.smiskinext.notification.application.command.SendInviteeRespondedEmailCommand;
import io.github.smiskinext.notification.application.service.SendInviteeRespondedEmailApplicationService;
import io.github.smiskinext.notification.domain.model.CalendarEmail;
import io.github.smiskinext.notification.domain.port.EmailSender;
import io.github.smiskinext.shared.domain.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SendInviteeRespondedEmailApplicationServiceTest {

    @Mock
    private EmailSender emailSender;

    @InjectMocks
    private SendInviteeRespondedEmailApplicationService service;

    @Test
    void execute_sendsEmailAndReturnsSuccess() {
        CalendarEmail email = new CalendarEmail(
                "organizer@example.com",
                "Invitee Responded",
                "An invitee has responded to your meeting.",
                "BEGIN:VCALENDAR\nEND:VCALENDAR",
                "REPLY",
                "response.ics");
        SendInviteeRespondedEmailCommand command = new SendInviteeRespondedEmailCommand(email);

        Result<?, ?> result = service.execute(command);

        assertThat(result.isSuccess()).isTrue();
        verify(emailSender).send(email);
    }
}
