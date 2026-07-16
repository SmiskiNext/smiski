package io.github.smiskinext.meet.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;

import java.util.Objects;

/**
 * Required display name for a meeting invitee. Must be non-blank, max 255 characters.
 */
public record InviteeDisplayName(String value) implements ValueObject {

    public static final int MAX_LENGTH = 255;

    public InviteeDisplayName {
        Objects.requireNonNull(value, "InviteeDisplayName value must not be null");
        if (value.isBlank())
            throw new IllegalArgumentException("InviteeDisplayName must not be blank");
        if (value.length() > MAX_LENGTH)
            throw new IllegalArgumentException(
                    "InviteeDisplayName must not exceed " + MAX_LENGTH + " characters");
    }

    public static InviteeDisplayName of(String raw) {
        return new InviteeDisplayName(raw.strip());
    }
}
