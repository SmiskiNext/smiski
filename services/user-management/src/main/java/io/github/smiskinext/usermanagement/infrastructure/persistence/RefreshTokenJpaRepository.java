package io.github.smiskinext.usermanagement.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshTokenJpaEntity r SET r.revokedAt = :revokedAt "
            + "WHERE r.userId = :userId AND r.revokedAt IS NULL")
    void updateRevokedAtByUserId(
            @Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
