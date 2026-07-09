package io.github.smiskinext.meet.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;

import java.util.Objects;

/**
 * Identity of the user who sent the invitation, backed by the Jira {@code accountId}.
 *
 * <p>Matches the {@code VARCHAR(128)} column {@code meeting_invitees.inviter_id}.
 */
public record InviterId(String value) implements ValueObject {

    public InviterId {
        Objects.requireNonNull(value, "InviterId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("InviterId value must not be blank");
        }
    }

    public static InviterId of(String value) {
        return new InviterId(value);
    }
}
