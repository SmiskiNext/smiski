package io.github.smiskinext.meet.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;

import java.util.Objects;

/**
 * Identity of a registered user, backed by the Jira {@code accountId}.
 *
 * <p>Used across the meet domain for hosts, participants, invitees, and join requesters.
 * Matches the {@code VARCHAR(128)} identity columns rather than a UUID.
 */
public record AccountId(String value) implements ValueObject {

    public AccountId {
        Objects.requireNonNull(value, "AccountId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("AccountId value must not be blank");
        }
    }

    public static AccountId of(String value) {
        return new AccountId(value);
    }
}
