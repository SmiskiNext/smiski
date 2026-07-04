package io.github.smiskinext.meetingmanagement.infrastructure.persistence;

import io.github.smiskinext.meetingmanagement.domain.model.ParticipantRole;
import io.github.smiskinext.meetingmanagement.domain.model.ParticipationLog;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitParticipantSid;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ParticipationLogId;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import io.github.smiskinext.meetingmanagement.domain.projection.ParticipantSummary;
import io.github.phunguy65.zms.shared.domain.valueobject.MeetingId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ParticipationLogRepositoryAdapter implements ParticipationLogRepository {

    private final ParticipationLogJpaRepository jpa;

    public ParticipationLogRepositoryAdapter(ParticipationLogJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ParticipationLog save(ParticipationLog log) {
        ParticipationLogJpaEntity entity;
        if (log.getId() != null) {
            entity = jpa.findById(log.getId().value()).orElseThrow();
            log.getLeftAt().ifPresent(entity::setLeftAt);
            log.getLivekitParticipantSid()
                    .map(LiveKitParticipantSid::value)
                    .ifPresent(entity::setLivekitParticipantSid);
        } else {
            entity = toEntity(log);
        }
        ParticipationLogJpaEntity saved = jpa.save(entity);
        if (log.getId() == null) {
            log.assignId(ParticipationLogId.of(saved.getId()));
        }
        return log;
    }

    @Override
    public Optional<ParticipationLog> findActiveBySid(LiveKitParticipantSid sid) {
        return jpa.findActiveBySid(sid.value()).map(this::toDomain);
    }

    @Override
    public Optional<ParticipationLog> findActiveByMeetingIdAndIdentity(
            UUID meetingId, LiveKitIdentity identity) {
        return jpa.findActiveByMeetingIdAndIdentity(meetingId, identity.value())
                .map(this::toDomain);
    }

    @Override
    public long countActiveByMeetingId(UUID meetingId) {
        return jpa.countActiveByMeetingId(meetingId);
    }

    @Override
    public List<ParticipationLog> findActiveByMeetingId(UUID meetingId) {
        return jpa.findActiveByMeetingId(meetingId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<ParticipationLog> findActiveByUserId(UUID userId) {
        return jpa.findActiveByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<ParticipationLog> findActiveByMeetingIdAndUserId(UUID meetingId, UUID userId) {
        return jpa.findActiveByMeetingIdAndUserId(meetingId, userId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<ParticipationLog> findActiveByMeetingIdAndDisplayName(
            UUID meetingId, String displayName) {
        return jpa.findActiveByMeetingIdAndDisplayName(meetingId, displayName).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<ParticipantSummary> findParticipantSummariesByMeetingId(UUID meetingId) {
        return jpa.findParticipantSummariesByMeetingId(meetingId);
    }

    @Override
    public boolean existsByMeetingIdAndUserId(UUID meetingId, UUID userId) {
        return jpa.existsByMeetingIdAndUserId(meetingId, userId);
    }

    @Override
    public List<ParticipantSummary> findDistinctParticipantSummariesByMeetingId(UUID meetingId) {
        return jpa.findDistinctSessionsByMeetingId(meetingId).stream()
                .map(this::toSummary)
                .toList();
    }

    private ParticipantSummary toSummary(ParticipationLogJpaEntity e) {
        return new ParticipantSummary(
                e.getId(),
                e.getMeetingId(),
                e.getUserId(),
                e.getDisplayName(),
                e.getRole(),
                e.getJoinedAt(),
                e.getLeftAt());
    }

    private ParticipationLog toDomain(ParticipationLogJpaEntity e) {
        return ParticipationLog.reconstitute(
                ParticipationLogId.of(e.getId()),
                MeetingId.of(e.getMeetingId()),
                e.getUserId(),
                e.getDisplayName(),
                ParticipantRole.valueOf(e.getRole()),
                LiveKitIdentity.of(e.getLivekitIdentity()),
                e.getLivekitParticipantSid() != null
                        ? LiveKitParticipantSid.of(e.getLivekitParticipantSid())
                        : null,
                e.getJoinedAt(),
                e.getLeftAt());
    }

    private ParticipationLogJpaEntity toEntity(ParticipationLog log) {
        return new ParticipationLogJpaEntity(
                log.getMeetingId().value(),
                log.getUserId().orElse(null),
                log.getDisplayName(),
                log.getRole().name(),
                log.getLivekitIdentity().value(),
                log.getLivekitParticipantSid().map(LiveKitParticipantSid::value).orElse(null),
                log.getJoinedAt(),
                log.getLeftAt().orElse(null));
    }
}
