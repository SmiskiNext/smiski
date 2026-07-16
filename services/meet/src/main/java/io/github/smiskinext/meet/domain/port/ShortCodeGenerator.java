package io.github.smiskinext.meet.domain.port;

import io.github.smiskinext.meet.domain.model.valueobject.ShortCode;

/**
 * Outbound port for generating candidate {@link ShortCode} values.
 *
 * <p>Each call yields a fresh, cryptographically random code. Uniqueness is not guaranteed by the
 * generator; it is enforced by the persistence unique constraint and the collision-retry policy in
 * the application layer.
 */
public interface ShortCodeGenerator {

    /**
     * Generates a random join code.
     *
     * @return a syntactically valid, non-guessable {@link ShortCode}
     */
    ShortCode generate();
}
