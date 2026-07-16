package io.github.smiskinext.shared.domain;

/**
 * Port for draining and publishing domain events from an aggregate. Services invoke
 * {@link #publishEventsOf(AggregateRoot)} as the final step of a write use case; the
 * implementation dispatches each registered event and clears the aggregate.
 */
public interface EventPublisher {

    void publishEventsOf(AggregateRoot<?> aggregate);
}
