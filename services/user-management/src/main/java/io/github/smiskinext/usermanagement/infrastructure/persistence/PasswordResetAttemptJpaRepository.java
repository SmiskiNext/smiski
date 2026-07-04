package io.github.smiskinext.usermanagement.infrastructure.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetAttemptJpaRepository
        extends JpaRepository<PasswordResetAttemptJpaEntity, Long> {

    /**
     * Counts password reset attempts for an email since a given timestamp.
     * Used for per-email rate limiting (e.g., 5/hour).
     */
    @Query("""
            SELECT COUNT(a) FROM PasswordResetAttemptJpaEntity a
            WHERE a.email = :email AND a.createdAt > :since
            """)
    long countByEmailAndCreatedAtAfter(@Param("email") String email, @Param("since") Instant since);

    /**
     * Counts password reset attempts from an IP address since a given timestamp.
     * Used for per-IP rate limiting (e.g., 20/hour).
     */
    @Query("""
            SELECT COUNT(a) FROM PasswordResetAttemptJpaEntity a
            WHERE a.ipAddress = :ipAddress AND a.createdAt > :since
            """)
    long countByIpAddressAndCreatedAtAfter(
            @Param("ipAddress") String ipAddress, @Param("since") Instant since);
}
