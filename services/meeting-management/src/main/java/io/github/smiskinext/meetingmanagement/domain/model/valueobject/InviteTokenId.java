package io.github.smiskinext.meetingmanagement.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;
import io.github.smiskinext.meetingmanagement.domain.model.InviteToken;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed identity for an {@link InviteToken}.
 */
public record InviteTokenId(UUID value) implements ValueObject {

    public InviteTokenId {
        Objects.requireNonNull(value, "InviteTokenId value must not be null");
    }

    public static InviteTokenId of(UUID value) {
        return new InviteTokenId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
