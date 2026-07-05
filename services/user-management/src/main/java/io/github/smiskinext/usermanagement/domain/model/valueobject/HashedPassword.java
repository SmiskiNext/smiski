package io.github.smiskinext.usermanagement.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;

/**
 * Value object wrapping an Argon2id password hash string.
 */
public record HashedPassword(String value) implements ValueObject {

    public HashedPassword {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }
    }

    public static HashedPassword of(String hash) {
        return new HashedPassword(hash);
    }
}
