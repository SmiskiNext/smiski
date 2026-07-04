package io.github.smiskinext.meetingmanagement.domain;

import io.github.phunguy65.zms.shared.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * A {@link DomainEvent} intended for external publishing via Kafka (CloudEvents 1.0).
 */
public interface PublishableEvent extends DomainEvent {

    /**
     * UUIDv7 unique identifier for this event instance. Used as CloudEvents {@code id}.
     */
    UUID eventId();

    /**
     * The ID of the aggregate that produced this event. Used as Kafka message key.
     */
    UUID aggregateId();

    /**
     * The aggregate type (e.g., {@code "meeting"}).
     */
    String aggregateType();

    /**
     * The event type with version suffix
     * (e.g., {@code "io.github.phunguy65.zms.meeting.scheduled.v1"}).
     * Used as CloudEvents {@code type}.
     */
    String eventType();

    /**
     * The Kafka topic (e.g., {@code "meeting-management.meeting.scheduled"}).
     */
    String topic();

    @Override
    Instant occurredAt();
}
