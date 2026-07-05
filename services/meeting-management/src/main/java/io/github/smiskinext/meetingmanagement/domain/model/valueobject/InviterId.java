package io.github.smiskinext.meetingmanagement.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;
import java.util.Objects;
import java.util.UUID;

/**
 * Identity of the user who sent the invitation.
 */
public record InviterId(UUID value) implements ValueObject {

    public InviterId {
        Objects.requireNonNull(value, "InviterId value must not be null");
    }

    public static InviterId of(UUID value) {
        return new InviterId(value);
    }
}
