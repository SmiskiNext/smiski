package io.github.smiskinext.usermanagement.domain.model.valueobject;

import io.github.phunguy65.zms.shared.domain.ValueObject;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Value object representing a validated username.
 *
 * <p>Format: {@code ^[a-zA-Z0-9_-]{3,30}$} — alphanumeric, underscore, hyphen; 3–30 chars.
 */
public record Username(String value) implements ValueObject {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,30}$");

    public Username {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        if (!USERNAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Username must be 3–30 characters and contain only letters, digits, _ or -: "
                            + value);
        }
    }

    /** Creates a {@link Username} from a raw string, validating format. */
    public static Username of(String raw) {
        return new Username(raw);
    }

    /**
     * Generates a candidate username for Google OAuth users.
     * Format: {@code "user_"} followed by the first 8 hex characters of a random UUID.
     * Callers must check uniqueness and retry if a collision occurs.
     */
    public static Username generateForGoogle() {
        String hex = UUID.randomUUID().toString().replace("-", "");
        return new Username("user_" + hex.substring(0, 8));
    }
}
