package io.github.smiskinext.notification.application.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.notification.domain.port.PendingJoinRequestStore;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseConnectionManagerTest {

    private final PendingJoinRequestStore pendingStore = mock(PendingJoinRequestStore.class);

    private SseProperties properties(long heartbeatSeconds) {
        SseProperties properties = new SseProperties();
        properties.setHostStreamTimeoutMs(300_000L);
        properties.setHeartbeatIntervalSeconds(heartbeatSeconds);
        return properties;
    }

    @Test
    void livePushReachesRegisteredEmitter() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(pendingStore.findPendingByMeetingId(meetingId)).thenReturn(List.of());

        try (MockedConstruction<SseEmitter> construction = mockConstruction(SseEmitter.class)) {
            SseConnectionManager manager =
                    new SseConnectionManager(properties(3600L), pendingStore);
            manager.subscribe(meetingId);
            SseEmitter emitter = construction.constructed().getFirst();

            manager.pushJoinRequestCreated(
                    meetingId,
                    new JoinRequestCreatedData(
                            "req-1", "account-1", "Alice", "https://cdn.example.com/a.png"));

            verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Test
    void pushToMeetingWithNoEmittersIsNoop() {
        UUID meetingId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> construction = mockConstruction(SseEmitter.class)) {
            SseConnectionManager manager =
                    new SseConnectionManager(properties(3600L), pendingStore);

            manager.pushJoinRequestCreated(
                    meetingId,
                    new JoinRequestCreatedData(
                            "req-1", "account-1", "Alice", "https://cdn.example.com/a.png"));

            assertThat(construction.constructed()).isEmpty();
        }
    }

    @Test
    void timeoutRemovesEmitterAndCancelsHeartbeat() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(pendingStore.findPendingByMeetingId(meetingId)).thenReturn(List.of());

        try (MockedConstruction<SseEmitter> construction = mockConstruction(SseEmitter.class)) {
            SseConnectionManager manager =
                    new SseConnectionManager(properties(3600L), pendingStore);
            manager.subscribe(meetingId);
            SseEmitter emitter = construction.constructed().getFirst();

            org.mockito.ArgumentCaptor<Runnable> timeoutCaptor =
                    org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(emitter).onTimeout(timeoutCaptor.capture());
            timeoutCaptor.getValue().run();

            org.mockito.Mockito.clearInvocations(emitter);
            manager.pushJoinRequestCreated(
                    meetingId,
                    new JoinRequestCreatedData(
                            "req-1", "account-1", "Alice", "https://cdn.example.com/a.png"));

            verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Test
    void heartbeatCommentEmittedOnIdleStream() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(pendingStore.findPendingByMeetingId(meetingId)).thenReturn(List.of());

        try (MockedConstruction<SseEmitter> construction = mockConstruction(SseEmitter.class)) {
            SseConnectionManager manager = new SseConnectionManager(properties(1L), pendingStore);
            manager.subscribe(meetingId);
            SseEmitter emitter = construction.constructed().getFirst();

            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> verify(emitter, atLeast(2))
                            .send(any(SseEmitter.SseEventBuilder.class)));
        }
    }
}
