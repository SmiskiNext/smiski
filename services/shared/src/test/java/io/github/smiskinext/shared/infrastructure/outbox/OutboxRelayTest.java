package io.github.smiskinext.shared.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.cloudevents.CloudEvent;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OutboxRelayTest {

    private OutboxStore outboxStore;
    private OutboxTransport transport;
    private CloudEventEncoder cloudEventEncoder;
    private OutboxProperties properties;
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        outboxStore = mock(OutboxStore.class);
        transport = mock(OutboxTransport.class);
        cloudEventEncoder = new CloudEventEncoder("test-service");
        properties = new OutboxProperties(
                new OutboxProperties.Relay(true, Duration.ofSeconds(5), 10),
                new OutboxProperties.Cloudevent("test-service"),
                "kafka");
        relay = new OutboxRelay(outboxStore, transport, cloudEventEncoder, properties);
    }

    @Nested
    class MalformedPayload {

        @Test
        void malformed_payload_is_not_published_and_failure_is_recorded() {
            UUID rowId = UUID.randomUUID();
            OutboxStore.OutboxRow malformedRow = new OutboxStore.OutboxRow(
                    rowId, "tenant-1", "agg-1", "topic", "not-a-valid-cloudevent");
            when(outboxStore.claimBatch(10)).thenReturn(List.of(malformedRow));

            relay.relay();

            verify(transport, never()).send(anyString(), anyString(), any(CloudEvent.class));
            verify(outboxStore).recordFailure(any(UUID.class), anyString());
        }
    }

    @Nested
    class TransportAbstraction {

        @Test
        void relay_depends_only_on_transport_abstraction() {
            assertThat(OutboxRelay.class.getDeclaredConstructors()[0].getParameterTypes())
                    .contains(OutboxTransport.class)
                    .doesNotContain(org.springframework.kafka.core.KafkaTemplate.class);
        }
    }

    @Nested
    class EmptyBatch {

        @Test
        void relay_does_nothing_when_no_rows() {
            when(outboxStore.claimBatch(10)).thenReturn(List.of());

            relay.relay();

            verify(transport, never()).send(anyString(), anyString(), any(CloudEvent.class));
        }
    }
}
