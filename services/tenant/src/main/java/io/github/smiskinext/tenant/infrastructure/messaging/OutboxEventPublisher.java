package io.github.smiskinext.tenant.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.shared.infrastructure.outbox.CloudEventEncoder;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapperRegistry;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import io.github.smiskinext.tenant.domain.event.PublishableEvent;
import io.github.smiskinext.tenant.domain.port.EventPublisher;
import io.github.smiskinext.tenant.infrastructure.persistence.OutboxEventJpaEntity;
import io.github.smiskinext.tenant.infrastructure.persistence.OutboxEventJpaRepository;

import org.springframework.stereotype.Component;

@Component
public class OutboxEventPublisher implements EventPublisher {

    private final OutboxEventJpaRepository outboxRepository;
    private final OutboxEventProtoMapperRegistry mapperRegistry;
    private final CloudEventEncoder cloudEventEncoder;

    public OutboxEventPublisher(
            OutboxEventJpaRepository outboxRepository,
            OutboxEventProtoMapperRegistry mapperRegistry,
            CloudEventEncoder cloudEventEncoder) {
        this.outboxRepository = outboxRepository;
        this.mapperRegistry = mapperRegistry;
        this.cloudEventEncoder = cloudEventEncoder;
    }

    @Override
    public void publish(PublishableEvent event) {
        OutboxEventProtoMapper<io.github.smiskinext.shared.domain.PublishableEvent> mapper =
                resolveMapper(event);
        Message protoMessage = mapper.toProto(event);

        String payload = cloudEventEncoder.encode(
                event.eventId(),
                event.eventType(),
                mapper.dataSchema(),
                event.occurredAt(),
                event.aggregateId(),
                protoMessage);

        String tenantId = TenantContext.getCurrentTenant();
        OutboxEventJpaEntity outboxRow = new OutboxEventJpaEntity(
                tenantId,
                event.aggregateId(),
                event.aggregateType(),
                event.eventType(),
                event.topic(),
                payload,
                event.occurredAt());
        outboxRepository.save(outboxRow);
    }

    @SuppressWarnings("unchecked")
    private OutboxEventProtoMapper<io.github.smiskinext.shared.domain.PublishableEvent>
            resolveMapper(PublishableEvent event) {
        return (OutboxEventProtoMapper<io.github.smiskinext.shared.domain.PublishableEvent>)
                (OutboxEventProtoMapper<?>) mapperRegistry.resolve(
                        (Class<io.github.smiskinext.shared.domain.PublishableEvent>)
                                (Class<?>) event.getClass());
    }
}
