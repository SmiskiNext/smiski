package io.github.smiskinext.usermanagement.infrastructure.persistence;

import static io.github.smiskinext.usermanagement.domain.model.PasswordResetToken.MAX_ATTEMPTS;

import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.domain.model.PasswordResetToken;
import io.github.smiskinext.usermanagement.domain.model.valueobject.PasswordResetTokenId;
import io.github.smiskinext.usermanagement.domain.port.PasswordResetTokenRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository {

    private final PasswordResetTokenJpaRepository jpa;

    public PasswordResetTokenRepositoryAdapter(PasswordResetTokenJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<PasswordResetToken> findValidByUserId(UserId userId) {
        return jpa.findLatestUnusedByUserId(userId.value(), MAX_ATTEMPTS).map(this::toDomain);
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        PasswordResetTokenJpaEntity entity = toEntity(token);
        PasswordResetTokenJpaEntity saved = jpa.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional
    public void invalidateAllByUserId(UserId userId) {
        jpa.invalidateAllByUserId(userId.value(), Instant.now());
    }

    private PasswordResetToken toDomain(PasswordResetTokenJpaEntity e) {
        return PasswordResetToken.reconstitute(
                PasswordResetTokenId.of(e.getId()),
                UserId.of(e.getUserId()),
                e.getOtpHash(),
                e.getExpiresAt(),
                e.getUsedAt(),
                e.getAttempts(),
                e.getLastAttemptTimestamp(),
                e.getCreatedAt());
    }

    private PasswordResetTokenJpaEntity toEntity(PasswordResetToken t) {
        return new PasswordResetTokenJpaEntity(
                t.getId().value(),
                t.getUserId().value(),
                t.getOtpHash(),
                t.getExpiresAt(),
                t.getUsedAt().orElse(null),
                t.getAttempts(),
                t.getLastAttemptTimestamp().orElse(null),
                t.getCreatedAt());
    }
}
