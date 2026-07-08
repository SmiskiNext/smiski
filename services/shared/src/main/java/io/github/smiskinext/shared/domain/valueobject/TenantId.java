package io.github.smiskinext.shared.domain.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;

import java.util.Objects;

/**
 * Value object representing a strongly-typed tenant identifier.
 * Wraps a {@link String} (Jira cloudId) to prevent accidental mixing with other identifiers.
 */
public record TenantId(String value) implements ValueObject {

    public TenantId {
        Objects.requireNonNull(value, "TenantId must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TenantId must not be blank");
        }
    }

    public static TenantId of(String value) {
        return new TenantId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
