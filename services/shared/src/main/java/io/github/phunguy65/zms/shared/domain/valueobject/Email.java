package io.github.phunguy65.zms.shared.domain.valueobject;

import io.github.phunguy65.zms.shared.domain.ValueObject;

/**
 * Value object representing a lowercase-normalized email address.
 *
 * <p>Format validation is delegated to the presentation layer ({@code @Email} Jakarta constraint).
 * This VO only normalizes (strip + lowercase) and guards against blank values.
 */
public record Email(String value) implements ValueObject {

    public Email {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        value = value.strip().toLowerCase();
    }

    public static Email of(String raw) {
        return new Email(raw);
    }
}
