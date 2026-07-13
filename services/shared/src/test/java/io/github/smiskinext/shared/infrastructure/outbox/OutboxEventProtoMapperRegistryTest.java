package io.github.smiskinext.shared.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.Message;
import com.google.protobuf.Struct;
import io.github.smiskinext.shared.domain.PublishableEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventProtoMapperRegistryTest {

    @Test
    void resolves_registered_mapper() {
        OutboxEventProtoMapper<TestEvent> mapper = new TestEventMapper();
        OutboxEventProtoMapperRegistry registry =
                new OutboxEventProtoMapperRegistry(List.of(mapper));

        OutboxEventProtoMapper<TestEvent> resolved = registry.resolve(TestEvent.class);
        assertThat(resolved).isSameAs(mapper);
    }

    @Test
    void throws_for_unregistered_event_type() {
        OutboxEventProtoMapperRegistry registry = new OutboxEventProtoMapperRegistry(List.of());

        assertThatThrownBy(() -> registry.resolve(TestEvent.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No OutboxEventProtoMapper registered");
    }

    record TestEvent(UUID eventId, String aggregateId) implements PublishableEvent {
        @Override
        public String aggregateType() {
            return "test";
        }

        @Override
        public String eventType() {
            return "test.event";
        }

        @Override
        public String topic() {
            return "test.topic";
        }

        @Override
        public Instant occurredAt() {
            return Instant.now();
        }
    }

    static class TestEventMapper implements OutboxEventProtoMapper<TestEvent> {
        @Override
        public Class<TestEvent> eventType() {
            return TestEvent.class;
        }

        @Override
        public String dataSchema() {
            return "test.schema";
        }

        @Override
        public Message toProto(TestEvent event) {
            return Struct.getDefaultInstance();
        }
    }
}
