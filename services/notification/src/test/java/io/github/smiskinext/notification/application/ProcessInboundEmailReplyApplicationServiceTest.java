package io.github.smiskinext.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.notification.application.command.ProcessInboundEmailReplyCommand;
import io.github.smiskinext.notification.application.service.ProcessInboundEmailReplyApplicationService;
import io.github.smiskinext.notification.domain.NotificationError;
import io.github.smiskinext.notification.domain.port.InboundEmailReplyProcessor;
import io.github.smiskinext.notification.domain.port.WebhookVerifier;
import io.github.smiskinext.shared.domain.Result;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessInboundEmailReplyApplicationServiceTest {

    @Mock
    private WebhookVerifier webhookVerifier;

    @Mock
    private InboundEmailReplyProcessor inboundEmailReplyProcessor;

    @InjectMocks
    private ProcessInboundEmailReplyApplicationService service;

    @Test
    void execute_invalidSignature_returnsFailureWithoutProcessing() {
        String payload = "{\"type\":\"email.received\"}";
        Map<String, String> headers = Map.of("svix-id", "msg_123");
        when(webhookVerifier.verify(payload, headers)).thenReturn(false);

        Result<?, NotificationError> result =
                service.execute(new ProcessInboundEmailReplyCommand(payload, headers));

        assertThat(result.isFailure()).isTrue();
        assertThat(((Result.Failure<?, NotificationError>) result).error())
                .isInstanceOf(NotificationError.InvalidSignature.class);
        verify(inboundEmailReplyProcessor, never()).processInboundEmail(payload);
    }

    @Test
    void execute_validSignature_delegatesToProcessor() {
        String payload = "{\"type\":\"email.received\"}";
        Map<String, String> headers = Map.of("svix-id", "msg_456");
        when(webhookVerifier.verify(payload, headers)).thenReturn(true);

        Result<?, NotificationError> result =
                service.execute(new ProcessInboundEmailReplyCommand(payload, headers));

        assertThat(result.isSuccess()).isTrue();
        verify(inboundEmailReplyProcessor).processInboundEmail(payload);
    }
}
