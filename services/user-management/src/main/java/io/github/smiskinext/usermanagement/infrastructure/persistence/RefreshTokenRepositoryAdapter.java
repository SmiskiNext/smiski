package io.github.smiskinext.usermanagement.infrastructure.persistence;

import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.domain.model.RefreshToken;
import io.github.smiskinext.usermanagement.domain.model.valueobject.RefreshTokenId;
import io.github.smiskinext.usermanagement.domain.port.RefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpa;

    public RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(this::toDomain);
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        RefreshTokenJpaEntity entity = toEntity(token);
        RefreshTokenJpaEntity saved = jpa.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional
    public void revokeAllByUserId(UserId userId) {
        jpa.updateRevokedAtByUserId(userId.value(), Instant.now());
    }

    private RefreshToken toDomain(RefreshTokenJpaEntity e) {
        return RefreshToken.reconstitute(
                RefreshTokenId.of(e.getId()),
                UserId.of(e.getUserId()),
                e.getTokenHash(),
                e.getExpiresAt(),
                e.getRevokedAt(),
                e.getCreatedAt());
    }

    private RefreshTokenJpaEntity toEntity(RefreshToken t) {
        return new RefreshTokenJpaEntity(
                t.getId().value(),
                t.getUserId().value(),
                t.getTokenHash(),
                t.getExpiresAt(),
                t.getRevokedAt().orElse(null),
                t.getCreatedAt());
    }
}
