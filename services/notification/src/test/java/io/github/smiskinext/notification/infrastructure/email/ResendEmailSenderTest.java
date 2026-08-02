package io.github.smiskinext.notification.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import io.github.smiskinext.notification.domain.model.CalendarEmail;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ResendEmailSenderTest {

    private final Resend resend = mock(Resend.class);
    private final Emails emails = mock(Emails.class);
    private final ResendEmailSender sender = new ResendEmailSender(resend, "noreply@smiski.dev");

    ResendEmailSenderTest() {
        when(resend.emails()).thenReturn(emails);
    }

    @Test
    void htmlBodyNonNullSetsHtmlOnOptions() throws ResendException {
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(new CreateEmailResponse());
        CalendarEmail email = new CalendarEmail(
                "bob@test.com",
                "Invite",
                "plain body",
                "<h1>HTML body</h1>",
                "BEGIN:VCALENDAR",
                "REQUEST",
                "invite.ics");

        sender.send(email);

        ArgumentCaptor<CreateEmailOptions> captor =
                ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());
        CreateEmailOptions options = captor.getValue();
        assertThat(options.getHtml()).isEqualTo("<h1>HTML body</h1>");
        assertThat(options.getText()).isEqualTo("plain body");
        assertThat(options.getTo()).contains("bob@test.com");
        assertThat(options.getFrom()).isEqualTo("noreply@smiski.dev");
    }

    @Test
    void htmlBodyNullDoesNotSetHtml() throws ResendException {
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(new CreateEmailResponse());
        CalendarEmail email = new CalendarEmail(
                "carol@test.com",
                "Update",
                "text only",
                null,
                "BEGIN:VCALENDAR",
                "REQUEST",
                "invite.ics");

        sender.send(email);

        ArgumentCaptor<CreateEmailOptions> captor =
                ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());
        CreateEmailOptions options = captor.getValue();
        assertThat(options.getHtml()).isNull();
        assertThat(options.getText()).isEqualTo("text only");
    }

    @Test
    void resendExceptionIsWrappedAsEmailDeliveryException() throws ResendException {
        when(emails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException("network error"));
        CalendarEmail email = new CalendarEmail(
                "fail@test.com",
                "Subject",
                "body",
                null,
                "BEGIN:VCALENDAR",
                "REQUEST",
                "invite.ics");

        assertThatThrownBy(() -> sender.send(email))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("fail@test.com");
    }
}
