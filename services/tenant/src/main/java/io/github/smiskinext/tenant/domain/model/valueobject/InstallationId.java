package io.github.smiskinext.tenant.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;

import java.util.Objects;

public record InstallationId(String value) implements ValueObject {

    public InstallationId {
        Objects.requireNonNull(value, "InstallationId must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("InstallationId must not be blank");
        }
    }
}
