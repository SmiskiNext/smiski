package io.github.smiskinext.meet.domain.port;

/**
 * Settings governing how many times a unique short code is retried before giving up.
 */
public interface ShortCodeAllocationSettings {

    /** Maximum unique-code allocation attempts before giving up. */
    int maxAttempts();
}
