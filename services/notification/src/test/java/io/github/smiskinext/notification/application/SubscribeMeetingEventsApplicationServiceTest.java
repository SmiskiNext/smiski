package io.github.smiskinext.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.notification.application.command.SubscribeMeetingEventsCommand;
import io.github.smiskinext.notification.application.result.SubscribeMeetingEventsResult;
import io.github.smiskinext.notification.application.service.SubscribeMeetingEventsApplicationService;
import io.github.smiskinext.shared.domain.Result;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class SubscribeMeetingEventsApplicationServiceTest {

    @Mock
    private SseSubscriptionPort sseSubscriptionPort;

    @InjectMocks
    private SubscribeMeetingEventsApplicationService service;

    @Test
    void execute_meetingStream_callsSubscribeAndReturnsEmitter() {
        UUID meetingId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        when(sseSubscriptionPort.subscribe(meetingId)).thenReturn(emitter);

        Result<SubscribeMeetingEventsResult, ?> result =
                service.execute(new SubscribeMeetingEventsCommand(meetingId, false, null));

        assertThat(result.isSuccess()).isTrue();
        assertThat(((Result.Success<SubscribeMeetingEventsResult, ?>) result)
                        .value()
                        .emitter())
                .isSameAs(emitter);
        verify(sseSubscriptionPort).subscribe(meetingId);
    }

    @Test
    void execute_requestStream_callsSubscribeRequestAndReturnsEmitter() {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        when(sseSubscriptionPort.subscribeRequest(requestId)).thenReturn(emitter);

        Result<SubscribeMeetingEventsResult, ?> result =
                service.execute(new SubscribeMeetingEventsCommand(meetingId, true, requestId));

        assertThat(result.isSuccess()).isTrue();
        assertThat(((Result.Success<SubscribeMeetingEventsResult, ?>) result)
                        .value()
                        .emitter())
                .isSameAs(emitter);
        verify(sseSubscriptionPort).subscribeRequest(requestId);
    }
}
