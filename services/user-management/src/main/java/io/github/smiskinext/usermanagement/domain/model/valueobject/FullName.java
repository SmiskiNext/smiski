package io.github.smiskinext.usermanagement.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;

/**
 * Value object representing a user's full name (non-blank, max 255 chars).
 */
public record FullName(String value) implements ValueObject {

    public FullName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("FullName must not be blank");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException("FullName must not exceed 255 characters");
        }
        value = value.strip();
    }

    public static FullName of(String raw) {
        return new FullName(raw);
    }
}
