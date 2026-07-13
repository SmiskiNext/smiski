package io.github.smiskinext.tenant.infrastructure.messaging;

import com.google.protobuf.util.JsonFormat;
import io.github.smiskinext.tenant.domain.event.PublishableEvent;
import io.github.smiskinext.tenant.domain.event.TenantInstalledEvent;
import io.github.smiskinext.tenant.domain.port.EventPublisher;
import io.github.smiskinext.tenant.infrastructure.persistence.OutboxEventJpaEntity;
import io.github.smiskinext.tenant.infrastructure.persistence.OutboxEventJpaRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class OutboxEventPublisher implements EventPublisher {

    private final OutboxEventJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(
            OutboxEventJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(PublishableEvent event) {
        String payload = buildCloudEventJson(event);
        OutboxEventJpaEntity outboxRow = new OutboxEventJpaEntity(
                event.aggregateId(),
                event.aggregateType(),
                event.eventType(),
                event.topic(),
                payload,
                event.occurredAt());
        outboxRepository.save(outboxRow);
    }

    private String buildCloudEventJson(PublishableEvent event) {
        if (!(event instanceof TenantInstalledEvent installedEvent)) {
            throw new IllegalArgumentException(
                    "Unsupported event type: " + event.getClass().getName());
        }

        String protoJson = toProtoJson(installedEvent);
        UUID cloudEventId = event.eventId();

        ObjectNode cloudEvent = objectMapper.createObjectNode();
        cloudEvent.put("specversion", "1.0");
        cloudEvent.put("id", cloudEventId.toString());
        cloudEvent.put("type", event.eventType());
        cloudEvent.put("source", "tenant-service");
        cloudEvent.put("datacontenttype", "application/json");
        cloudEvent.put("dataschema", "io.github.smiskinext.event.tenant.v1.TenantInstalled");
        cloudEvent.put("time", event.occurredAt().toString());
        cloudEvent.put("subject", event.aggregateId());

        try {
            cloudEvent.set("data", objectMapper.readTree(protoJson));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse proto JSON", e);
        }

        try {
            return objectMapper.writeValueAsString(cloudEvent);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize CloudEvent", e);
        }
    }

    private String toProtoJson(TenantInstalledEvent event) {
        try {
            return JsonFormat.printer().print(TenantEventProtoMapper.toProto(event));
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalStateException("Failed to convert proto to JSON", e);
        }
    }
}
