package io.github.smiskinext.meet.domain.event;

/**
 * Meet-internal marker for {@link io.github.smiskinext.shared.domain.PublishableEvent} instances
 * that must reach a meeting host in real time via SSE.
 *
 * <p>An event carrying this marker triggers an immediate post-commit relay of its already-persisted
 * outbox row, so the corresponding CloudEvent is published without waiting for the scheduled outbox
 * poll. Events that do not carry the marker are delivered by the scheduled poll only.
 *
 * <p>The marker is framework-agnostic (no Spring, Kafka, or JPA imports) so it stays within the
 * domain layer's architectural constraints. Future real-time events opt in simply by implementing
 * this interface; the relay trigger requires no change.
 */
public interface SseTriggeringEvent {}
