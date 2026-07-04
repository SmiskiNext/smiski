package io.github.smiskinext.usermanagement.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import io.github.smiskinext.usermanagement.domain.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenJpaRepository
        extends JpaRepository<PasswordResetTokenJpaEntity, UUID> {

    /**
     * Finds the most recent non-used, non-locked token for a user.
     * Note: Does NOT filter by expiry - caller must check {@code isExpired()} to provide specific error.
     *
     * @param userId      the user ID to search for
     * @param maxAttempts maximum attempts threshold (use {@link PasswordResetToken#MAX_ATTEMPTS})
     */
    @Query("""
            SELECT t FROM PasswordResetTokenJpaEntity t
            WHERE t.userId = :userId
              AND t.usedAt IS NULL
              AND t.attempts < :maxAttempts
            ORDER BY t.createdAt DESC
            LIMIT 1
            """)
    Optional<PasswordResetTokenJpaEntity> findLatestUnusedByUserId(
            @Param("userId") UUID userId, @Param("maxAttempts") int maxAttempts);

    /**
     * Marks all unused tokens for a user as used (invalidates them).
     */
    @Modifying
    @Query("""
            UPDATE PasswordResetTokenJpaEntity t
            SET t.usedAt = :now
            WHERE t.userId = :userId AND t.usedAt IS NULL
            """)
    void invalidateAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
