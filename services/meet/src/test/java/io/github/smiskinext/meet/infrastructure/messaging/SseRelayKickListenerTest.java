package io.github.smiskinext.meet.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.smiskinext.meet.domain.event.JoinRequestCreatedEvent;
import io.github.smiskinext.meet.domain.event.SseTriggeringEvent;
import io.github.smiskinext.shared.domain.PublishableEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxRelay;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SseRelayKickListenerTest {

    private OutboxRelay outboxRelay;
    private SseRelayKickListener listener;

    @BeforeEach
    void setUp() {
        outboxRelay = mock(OutboxRelay.class);
        Executor synchronousExecutor = Runnable::run;
        listener = new SseRelayKickListener(outboxRelay, synchronousExecutor);
    }

    @Test
    void markedEventTriggersRelay() {
        SseTriggeringEvent event = markedEvent();

        listener.onSseTriggeringEvent(event);

        verify(outboxRelay).relay();
    }

    @Test
    void unmarkedPublishableEventIsNotEligibleForImmediateRelay() {
        PublishableEvent unmarked = unmarkedPublishableEvent();

        assertThatCode(() -> {
                    if (unmarked instanceof SseTriggeringEvent marked) {
                        listener.onSseTriggeringEvent(marked);
                    }
                })
                .doesNotThrowAnyException();

        verify(outboxRelay, never()).relay();
    }

    @Test
    void relayExceptionIsSwallowed() {
        doThrow(new RuntimeException("kafka down")).when(outboxRelay).relay();

        assertThatCode(() -> listener.onSseTriggeringEvent(markedEvent()))
                .doesNotThrowAnyException();

        verify(outboxRelay).relay();
    }

    private SseTriggeringEvent markedEvent() {
        return new JoinRequestCreatedEvent(
                UUID.randomUUID(),
                "tenant-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "account-1",
                "Alice",
                "device-1",
                null,
                Instant.now());
    }

    private PublishableEvent unmarkedPublishableEvent() {
        return new PublishableEvent() {
            @Override
            public UUID eventId() {
                return UUID.randomUUID();
            }

            @Override
            public String aggregateId() {
                return "aggregate-1";
            }

            @Override
            public String aggregateType() {
                return "meeting";
            }

            @Override
            public String eventType() {
                return "io.github.smiskinext.meet.test.unmarked.v1";
            }

            @Override
            public String topic() {
                return "meet.test.unmarked";
            }

            @Override
            public Instant occurredAt() {
                return Instant.now();
            }
        };
    }
}
