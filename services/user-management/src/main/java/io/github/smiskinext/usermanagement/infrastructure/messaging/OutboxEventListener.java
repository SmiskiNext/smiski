package io.github.smiskinext.usermanagement.infrastructure.messaging;

import io.github.smiskinext.usermanagement.domain.PublishableEvent;
import io.github.smiskinext.usermanagement.infrastructure.persistence.OutboxEventJpaEntity;
import io.github.smiskinext.usermanagement.infrastructure.persistence.OutboxEventRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/**
 * Listens for {@link PublishableEvent}s published within a transaction and persists them to the
 * {@code outbox_event} table after the transaction commits.
 */
@Component
public class OutboxEventListener {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventListener.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventListener(
            OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPublishableEvent(PublishableEvent event) {
        String payload = objectMapper.writeValueAsString(event);
        var entity = new OutboxEventJpaEntity(
                event.aggregateId(),
                event.aggregateType(),
                event.eventType(),
                event.topic(),
                payload,
                Instant.now());
        outboxEventRepository.save(entity);
        log.debug(
                "Outbox row created: eventType={}, aggregateId={}",
                event.eventType(),
                event.aggregateId());
    }
}
