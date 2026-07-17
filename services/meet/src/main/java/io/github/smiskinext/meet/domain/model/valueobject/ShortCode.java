package io.github.smiskinext.meet.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;

import java.util.Locale;
import java.util.Objects;

/**
 * Short alphanumeric code used to join a meeting (e.g. "a1b2c3d4e5").
 *
 * <p>Values are normalized to lower case so lookups are case-insensitive. Generation lives behind
 * the {@code ShortCodeGenerator} port; uniqueness is enforced by the database UNIQUE constraint and
 * the application-layer collision-retry policy.
 */
public record ShortCode(String value) implements ValueObject {

    private static final int MAX_LENGTH = 15;

    public ShortCode {
        Objects.requireNonNull(value, "ShortCode value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("ShortCode must not be blank");
        if (value.length() > MAX_LENGTH)
            throw new IllegalArgumentException("ShortCode must not exceed 15 characters");
    }

    public static ShortCode of(String raw) {
        return new ShortCode(raw);
    }

    @Override
    public String value() {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
