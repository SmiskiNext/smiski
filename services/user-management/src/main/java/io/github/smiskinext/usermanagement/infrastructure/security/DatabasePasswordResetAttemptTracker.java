package io.github.smiskinext.usermanagement.infrastructure.security;

import io.github.smiskinext.usermanagement.domain.port.PasswordResetAttemptTracker;
import io.github.smiskinext.usermanagement.infrastructure.persistence.PasswordResetAttemptJpaEntity;
import io.github.smiskinext.usermanagement.infrastructure.persistence.PasswordResetAttemptJpaRepository;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database-backed implementation of password reset attempt tracking.
 */
@Component
public class DatabasePasswordResetAttemptTracker implements PasswordResetAttemptTracker {

    private final PasswordResetAttemptJpaRepository attemptRepository;

    public DatabasePasswordResetAttemptTracker(
            PasswordResetAttemptJpaRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    @Override
    @Transactional
    public void recordAttempt(String email, @Nullable String ipAddress) {
        var attempt = new PasswordResetAttemptJpaEntity(email, ipAddress);
        attemptRepository.save(attempt);
    }

    @Override
    public long countAttemptsSince(String email, Instant since) {
        return attemptRepository.countByEmailAndCreatedAtAfter(email, since);
    }
}
