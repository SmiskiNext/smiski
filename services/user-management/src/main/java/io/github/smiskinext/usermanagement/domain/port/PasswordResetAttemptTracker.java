package io.github.smiskinext.usermanagement.domain.port;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Outbound port: tracking password reset attempts for CAPTCHA enforcement.
 */
public interface PasswordResetAttemptTracker {

    /**
     * Records a password reset attempt.
     *
     * @param email the email address
     * @param ipAddress the source IP address, if available
     */
    void recordAttempt(String email, @Nullable String ipAddress);

    /**
     * Counts password reset attempts for an email since a given timestamp.
     *
     * @param email the email address
     * @param since the start of the time window
     * @return the number of attempts
     */
    long countAttemptsSince(String email, Instant since);
}
