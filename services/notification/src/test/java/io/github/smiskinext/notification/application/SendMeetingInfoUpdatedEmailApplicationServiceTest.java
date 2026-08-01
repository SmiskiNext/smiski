package io.github.smiskinext.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.github.smiskinext.notification.application.command.SendMeetingInfoUpdatedEmailCommand;
import io.github.smiskinext.notification.application.service.SendMeetingInfoUpdatedEmailApplicationService;
import io.github.smiskinext.notification.domain.model.CalendarEmail;
import io.github.smiskinext.notification.domain.port.EmailSender;
import io.github.smiskinext.shared.domain.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SendMeetingInfoUpdatedEmailApplicationServiceTest {

    @Mock
    private EmailSender emailSender;

    @InjectMocks
    private SendMeetingInfoUpdatedEmailApplicationService service;

    @Test
    void execute_sendsEmailAndReturnsSuccess() {
        CalendarEmail email = new CalendarEmail(
                "invitee@example.com",
                "Meeting Updated",
                "The meeting details have changed.",
                "BEGIN:VCALENDAR\nEND:VCALENDAR",
                "REQUEST",
                "update.ics");
        SendMeetingInfoUpdatedEmailCommand command = new SendMeetingInfoUpdatedEmailCommand(email);

        Result<?, ?> result = service.execute(command);

        assertThat(result.isSuccess()).isTrue();
        verify(emailSender).send(email);
    }
}
