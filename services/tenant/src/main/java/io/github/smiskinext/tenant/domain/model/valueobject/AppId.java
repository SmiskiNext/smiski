package io.github.smiskinext.tenant.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;

import java.util.Objects;

public record AppId(String value) implements ValueObject {

    public AppId {
        Objects.requireNonNull(value, "AppId must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("AppId must not be blank");
        }
    }
}
