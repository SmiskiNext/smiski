package io.github.smiskinext.meet.infrastructure.persistence;

import io.github.smiskinext.meet.domain.model.CloseReason;
import io.github.smiskinext.meet.domain.model.ParticipantRole;
import io.github.smiskinext.meet.domain.model.ParticipationLog;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitParticipantSid;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.meet.domain.model.valueobject.ParticipationLogId;
import io.github.smiskinext.shared.domain.valueobject.TenantId;

/**
 * Maps between {@link ParticipationLogJpaEntity} and the {@link ParticipationLog} domain aggregate.
 */
final class ParticipationLogPersistenceMapper {

    private ParticipationLogPersistenceMapper() {}

    static ParticipationLogJpaEntity toEntity(ParticipationLog log) {
        return new ParticipationLogJpaEntity(
                log.getId().value(),
                log.getMeetingId().value(),
                log.getAccountId().value(),
                log.getDisplayName(),
                log.getRole().name(),
                log.getLivekitIdentity().value(),
                log.getLivekitParticipantSid().map(LiveKitParticipantSid::value).orElse(null),
                log.getJoinedAt(),
                log.getLeftAt().orElse(null),
                log.getDisplayNameCachedAt().orElse(null),
                log.getCloseReason().map(CloseReason::name).orElse(null));
    }

    static ParticipationLog toDomain(ParticipationLogJpaEntity entity) {
        return ParticipationLog.reconstitute(
                TenantId.of(entity.getTenantId()),
                ParticipationLogId.of(entity.getId()),
                MeetingId.of(entity.getMeetingId()),
                AccountId.of(entity.getAccountId()),
                entity.getDisplayName(),
                entity.getDisplayNameCachedAt(),
                ParticipantRole.valueOf(entity.getRole()),
                LiveKitIdentity.of(entity.getLivekitIdentity()),
                entity.getLivekitParticipantSid() != null
                        ? LiveKitParticipantSid.of(entity.getLivekitParticipantSid())
                        : null,
                entity.getJoinedAt(),
                entity.getLeftAt(),
                entity.getCloseReason() != null
                        ? CloseReason.valueOf(entity.getCloseReason())
                        : null);
    }
}
