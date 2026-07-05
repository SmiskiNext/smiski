package io.github.smiskinext.usermanagement.domain.port;

import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.domain.model.PasswordResetToken;
import java.util.Optional;

/**
 * Outbound port: persistence operations for the {@link PasswordResetToken} aggregate.
 */
public interface PasswordResetTokenRepository {

    /**
     * Finds the most recent valid (not used, not expired) token for the given user.
     *
     * @param userId the user ID
     * @return the valid token if exists
     */
    Optional<PasswordResetToken> findValidByUserId(UserId userId);

    /**
     * Saves a password reset token.
     *
     * @param token the token to save
     * @return the saved token
     */
    PasswordResetToken save(PasswordResetToken token);

    /**
     * Invalidates all unused tokens for a user.
     * Called when a new token is issued to ensure only one active token per user.
     *
     * @param userId the user ID
     */
    void invalidateAllByUserId(UserId userId);
}
