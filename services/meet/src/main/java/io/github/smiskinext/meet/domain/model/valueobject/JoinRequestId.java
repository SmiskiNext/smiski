package io.github.smiskinext.meet.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a join request in the admission queue.
 */
public record JoinRequestId(UUID value) implements ValueObject {

    public JoinRequestId {
        Objects.requireNonNull(value, "JoinRequestId value must not be null");
    }

    public static JoinRequestId of(UUID value) {
        return new JoinRequestId(value);
    }

    public static JoinRequestId generate() {
        return new JoinRequestId(UUID.randomUUID());
    }
}
