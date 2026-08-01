package io.github.smiskinext.notification.infrastructure.sse;

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

import io.github.smiskinext.notification.domain.model.JoinDecision;
import io.github.smiskinext.notification.domain.model.PendingJoinRequest;
import io.github.smiskinext.notification.domain.port.JoinDecisionStore;
import io.github.smiskinext.notification.domain.port.PendingJoinRequestStore;
import io.github.smiskinext.notification.infrastructure.config.SseProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseConnectionManagerTest {

    private final PendingJoinRequestStore pendingStore = mock(PendingJoinRequestStore.class);
    private final JoinDecisionStore decisionStore = mock(JoinDecisionStore.class);

    private SseProperties properties(long heartbeatSeconds) {
        SseProperties properties = new SseProperties();
        properties.setHostStreamTimeoutMs(300_000L);
        properties.setHeartbeatIntervalSeconds(heartbeatSeconds);
        return properties;
    }

    private SseConnectionManager manager(long heartbeatSeconds) {
        return new SseConnectionManager(properties(heartbeatSeconds), pendingStore, decisionStore);
    }

    private static PendingJoinRequest pendingRequest(UUID meetingId, UUID requestId) {
        return new PendingJoinRequest(
                requestId,
                meetingId,
                "account-1",
                "Alice",
                "device-1",
                "https://cdn.example.com/a.png",
                Instant.now().plusSeconds(300));
    }

    @Test
    void livePushReachesRegisteredEmitter() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(pendingStore.findPendingByMeetingId(meetingId)).thenReturn(List.of());

        try (MockedConstruction<SseEmitter> construction = mockConstruction(SseEmitter.class)) {
            SseConnectionManager manager = manager(3600L);
            manager.subscribe(meetingId);
            SseEmitter emitter = construction.constructed().getFirst();

            manager.pushJoinRequestCreated(meetingId, pendingRequest(meetingId, requestId));

            verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Test
    void requesterLiveApprovedPushReachesRegisteredEmitter() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(decisionStore.findByRequestId(requestId)).thenReturn(Optional.empty());

        try (MockedConstruction<SseEmitter> construction = mockConstruction(SseEmitter.class)) {
            SseConnectionManager manager = manager(3600L);
            manager.subscribeRequest(requestId);
            SseEmitter emitter = construction.constructed().getFirst();

            manager.pushJoinResolved(
                    requestId, JoinDecision.approved(requestId, "the-token", "meeting-1"));

            verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Test
    void requesterLiveDeniedPushReachesRegisteredEmitter() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(decisionStore.findByRequestId(requestId)).thenReturn(Optional.empty());

        try (MockedConstruction<SseEmitter> construction = mockConstruction(SseEmitter.class)) {
            SseConnectionManager manager = manager(3600L);
            manager.subscribeRequest(requestId);
            SseEmitter emitter = construction.constructed().getFirst();

            manager.pushJoinResolved(requestId, JoinDecision.denied(requestId, null));

            verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Test
    void requesterSubscribeReplaysRecordedDecision() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(decisionStore.findByRequestId(requestId))
                .thenReturn(
                        Optional.of(JoinDecision.approved(requestId, "the-token", "meeting-1")));

        try (MockedConstruction<SseEmitter> construction = mockConstruction(SseEmitter.class)) {
            SseConnectionManager manager = manager(3600L);
            manager.subscribeRequest(requestId);
            SseEmitter emitter = construction.constructed().getFirst();

            verify(emitter, atLeast(2)).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Test
    void requesterTimeoutRemovesEmitterAndCancelsHeartbeat() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(decisionStore.findByRequestId(requestId)).thenReturn(Optional.empty());

        try (MockedConstruction<SseEmitter> construction = mockConstruction(SseEmitter.class)) {
            SseConnectionManager manager = manager(3600L);
            manager.subscribeRequest(requestId);
            SseEmitter emitter = construction.constructed().getFirst();

            org.mockito.ArgumentCaptor<Runnable> timeoutCaptor =
                    org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(emitter).onTimeout(timeoutCaptor.capture());
            timeoutCaptor.getValue().run();

            org.mockito.Mockito.clearInvocations(emitter);
            manager.pushJoinResolved(
                    requestId, JoinDecision.approved(requestId, "the-token", "meeting-1"));

            verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Test
    void pushToMeetingWithNoEmittersIsNoop() {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> construction = mockConstruction(SseEmitter.class)) {
            SseConnectionManager manager = manager(3600L);

            manager.pushJoinRequestCreated(meetingId, pendingRequest(meetingId, requestId));

            assertThat(construction.constructed()).isEmpty();
        }
    }

    @Test
    void timeoutRemovesEmitterAndCancelsHeartbeat() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(pendingStore.findPendingByMeetingId(meetingId)).thenReturn(List.of());

        try (MockedConstruction<SseEmitter> construction = mockConstruction(SseEmitter.class)) {
            SseConnectionManager manager = manager(3600L);
            manager.subscribe(meetingId);
            SseEmitter emitter = construction.constructed().getFirst();

            org.mockito.ArgumentCaptor<Runnable> timeoutCaptor =
                    org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(emitter).onTimeout(timeoutCaptor.capture());
            timeoutCaptor.getValue().run();

            org.mockito.Mockito.clearInvocations(emitter);
            manager.pushJoinRequestCreated(meetingId, pendingRequest(meetingId, requestId));

            verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Test
    void heartbeatCommentEmittedOnIdleStream() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(pendingStore.findPendingByMeetingId(meetingId)).thenReturn(List.of());

        try (MockedConstruction<SseEmitter> construction = mockConstruction(SseEmitter.class)) {
            SseConnectionManager manager = manager(1L);
            manager.subscribe(meetingId);
            SseEmitter emitter = construction.constructed().getFirst();

            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> verify(emitter, atLeast(2))
                            .send(any(SseEmitter.SseEventBuilder.class)));
        }
    }
}
