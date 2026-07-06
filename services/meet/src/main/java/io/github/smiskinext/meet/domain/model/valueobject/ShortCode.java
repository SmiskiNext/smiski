package io.github.smiskinext.meet.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * Short alphanumeric code used to join a meeting (e.g. "A1B2C3D4E5").
 *
 * <p>Generation uses a cryptographically secure random source. Uniqueness is enforced by the
 * database UNIQUE constraint; the application layer is responsible for collision-retry logic.
 */
public record ShortCode(String value) implements ValueObject {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    public ShortCode {
        Objects.requireNonNull(value, "ShortCode value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("ShortCode must not be blank");
        if (value.length() > 15)
            throw new IllegalArgumentException("ShortCode must not exceed 15 characters");
    }

    /**
     * Generates a random 10-character alphanumeric code.
     */
    public static ShortCode generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return new ShortCode(sb.toString());
    }

    public static ShortCode of(String raw) {
        return new ShortCode(raw);
    }
}
