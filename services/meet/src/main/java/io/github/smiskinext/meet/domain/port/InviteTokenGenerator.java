package io.github.smiskinext.meet.domain.port;

import java.time.Instant;

/**
 * Outbound port for generating invite tokens.
 *
 * <p>Returns a raw token string along with its SHA-256 hash and expiry.
 * Only the hash is persisted; the raw token is distributed via events.
 */
public interface InviteTokenGenerator {

    /**
     * Generates a single-use invite token.
     *
     * <p>The token is opaque randomness; it carries no meeting or invitee data. Binding to a
     * meeting and invitee is established by the persisted invitee row keyed on the token hash.
     *
     * @return token generation result containing raw token, hash, and expiry
     */
    TokenResult generate();

    /**
     * Result of token generation.
     *
     * @param rawToken the raw token string (never persisted, carried in events)
     * @param tokenHash SHA-256 hex-encoded hash of the raw token
     * @param expiresAt the instant at which the token expires
     */
    record TokenResult(String rawToken, String tokenHash, Instant expiresAt) {}
}
