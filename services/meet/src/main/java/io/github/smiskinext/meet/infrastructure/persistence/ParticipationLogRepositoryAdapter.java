package io.github.smiskinext.meet.infrastructure.persistence;

import io.github.smiskinext.meet.domain.model.ParticipationLog;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitParticipantSid;
import io.github.smiskinext.meet.domain.port.ParticipationLogRepository;
import io.github.smiskinext.meet.domain.projection.ParticipantSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ParticipationLogRepositoryAdapter implements ParticipationLogRepository {

    private final ParticipationLogJpaRepository jpaRepository;

    public ParticipationLogRepositoryAdapter(ParticipationLogJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ParticipationLog save(ParticipationLog log) {
        ParticipationLogJpaEntity entity = ParticipationLogPersistenceMapper.toEntity(log);
        jpaRepository.save(entity);
        return log;
    }

    /** TODO: Implement in a later slice for webhook handler. */
    @Override
    public Optional<ParticipationLog> findActiveBySid(LiveKitParticipantSid sid) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    /** TODO: Implement in a later slice for webhook handler. */
    @Override
    public Optional<ParticipationLog> findActiveByMeetingIdAndIdentity(
            UUID meetingId, LiveKitIdentity identity) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    /** TODO: Implement in a later slice. */
    @Override
    public long countActiveByMeetingId(UUID meetingId) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    /** TODO: Implement in a later slice. */
    @Override
    public List<ParticipationLog> findActiveByMeetingId(UUID meetingId) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    /** TODO: Implement in a later slice. */
    @Override
    public List<ParticipationLog> findActiveByAccountId(AccountId accountId) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    /** TODO: Implement in a later slice. */
    @Override
    public List<ParticipationLog> findActiveByMeetingIdAndAccountId(
            UUID meetingId, AccountId accountId) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    /** TODO: Implement in a later slice. */
    @Override
    public List<ParticipationLog> findActiveByMeetingIdAndDisplayName(
            UUID meetingId, String displayName) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    /** TODO: Implement in a later slice. */
    @Override
    public List<ParticipantSummary> findParticipantSummariesByMeetingId(UUID meetingId) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    /** TODO: Implement in a later slice. */
    @Override
    public boolean existsByMeetingIdAndAccountId(UUID meetingId, AccountId accountId) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    @Override
    public List<ParticipantSummary> findDistinctParticipantSummariesByMeetingId(UUID meetingId) {
        Map<String, List<ParticipationLogJpaEntity>> sessionsByAccount = new LinkedHashMap<>();
        for (ParticipationLogJpaEntity session : jpaRepository.findByMeetingId(meetingId)) {
            sessionsByAccount
                    .computeIfAbsent(session.getAccountId(), account -> new ArrayList<>())
                    .add(session);
        }
        List<ParticipantSummary> participants = new ArrayList<>(sessionsByAccount.size());
        for (List<ParticipationLogJpaEntity> sessions : sessionsByAccount.values()) {
            participants.add(collapse(sessions));
        }
        return participants;
    }

    private static ParticipantSummary collapse(List<ParticipationLogJpaEntity> sessions) {
        ParticipationLogJpaEntity mostRecent = sessions.getFirst();
        Instant earliestJoinedAt = mostRecent.getJoinedAt();
        boolean anyStillOpen = false;
        Instant latestLeftAt = null;
        for (ParticipationLogJpaEntity session : sessions) {
            if (session.getJoinedAt().isBefore(earliestJoinedAt)) {
                earliestJoinedAt = session.getJoinedAt();
            }
            if (session.getJoinedAt().isAfter(mostRecent.getJoinedAt())) {
                mostRecent = session;
            }
            if (session.getLeftAt() == null) {
                anyStillOpen = true;
            } else if (latestLeftAt == null || session.getLeftAt().isAfter(latestLeftAt)) {
                latestLeftAt = session.getLeftAt();
            }
        }
        return new ParticipantSummary(
                mostRecent.getId(),
                mostRecent.getMeetingId(),
                mostRecent.getAccountId(),
                mostRecent.getDisplayName(),
                mostRecent.getRole(),
                earliestJoinedAt,
                anyStillOpen ? null : latestLeftAt);
    }
}
