package io.github.smiskinext.notification.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import io.github.smiskinext.notification.infrastructure.config.NotificationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ResendEmailSenderTest {

    private Resend resend;
    private ResendEmailSender sender;

    @BeforeEach
    void setUp() {
        resend = mock(Resend.class, RETURNS_DEEP_STUBS);
        NotificationProperties properties = new NotificationProperties();
        properties.getResend().setApiKey("re_test_key");
        properties.getResend().setFromEmail("notifications@example.com");
        properties.getResend().setFromName("Zero Meeting System");
        properties.getInvitation().setJoinBaseUrl("https://app.example.com/join");
        properties.afterPropertiesSet();
        sender = new ResendEmailSender(resend, properties);
    }

    @Test
    void sendsEmailWithExpectedResendOptions() throws Exception {
        CreateEmailResponse response = mock(CreateEmailResponse.class);
        when(response.getId()).thenReturn("email_123");
        when(resend.emails().send(any(CreateEmailOptions.class))).thenReturn(response);

        sender.send("alice@example.com", "Invitation", "<p>Hello</p>");

        ArgumentCaptor<CreateEmailOptions> captor =
                ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(resend.emails()).send(captor.capture());
        CreateEmailOptions options = captor.getValue();
        verify(response).getId();
        org.assertj.core.api.Assertions.assertThat(options.getFrom())
                .isEqualTo("Zero Meeting System <notifications@example.com>");
        org.assertj.core.api.Assertions.assertThat(options.getTo()).contains("alice@example.com");
        org.assertj.core.api.Assertions.assertThat(options.getSubject()).isEqualTo("Invitation");
    }

    @Test
    void wrapsProviderFailure() throws Exception {
        when(resend.emails().send(any(CreateEmailOptions.class)))
                .thenThrow(new RuntimeException("provider down"));

        assertThatThrownBy(() -> sender.send("alice@example.com", "Invitation", "<p>Hello</p>"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to send invitation email via Resend");
    }
}
