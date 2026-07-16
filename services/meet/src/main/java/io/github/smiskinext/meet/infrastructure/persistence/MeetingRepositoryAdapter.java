package io.github.smiskinext.meet.infrastructure.persistence;

import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.ParticipatedMeetingCursor;
import io.github.smiskinext.meet.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meet.domain.model.valueobject.ShortCodeCollisionException;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.projection.MeetingSummary;
import io.github.smiskinext.meet.domain.projection.ParticipatedMeetingSummary;
import io.github.smiskinext.shared.domain.CursorPageResponse;
import io.github.smiskinext.shared.domain.ScrollCursor;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class MeetingRepositoryAdapter implements MeetingRepository {

    private final MeetingJpaRepository jpaRepository;

    public MeetingRepositoryAdapter(MeetingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    private static final String SHORT_CODE_CONSTRAINT = "uq_meetings_short_code";

    @Override
    public Meeting save(Meeting meeting) {
        MeetingJpaEntity entity = MeetingPersistenceMapper.toEntity(meeting);
        try {
            jpaRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            if (isShortCodeCollision(e)) {
                throw new ShortCodeCollisionException(meeting.getShortCode().value(), e);
            }
            throw e;
        }
        return meeting;
    }

    private static boolean isShortCodeCollision(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null
                    && message.toLowerCase(Locale.ROOT).contains(SHORT_CODE_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<Meeting> findById(UUID id) {
        return jpaRepository.findById(id).map(MeetingPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Meeting> findByIdWithLock(UUID id) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    @Override
    public Optional<Meeting> findByShortCode(ShortCode shortCode) {
        return jpaRepository
                .findByShortCode(shortCode.value())
                .map(MeetingPersistenceMapper::toDomain);
    }

    /** TODO: Implement in a later slice for cursor-paginated host meeting list. */
    @Override
    public CursorPageResponse<MeetingSummary> findSummariesByHostId(
            AccountId hostId, @Nullable ScrollCursor cursor, int pageSize) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    /** TODO: Implement in a later slice for participated meetings list. */
    @Override
    public CursorPageResponse<ParticipatedMeetingSummary> findParticipatedSummariesByAccountId(
            AccountId accountId,
            Set<MeetingStatus> statuses,
            @Nullable ParticipatedMeetingCursor cursor,
            int pageSize) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }
}
