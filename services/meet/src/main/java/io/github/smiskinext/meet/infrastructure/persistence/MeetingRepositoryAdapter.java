package io.github.smiskinext.meet.infrastructure.persistence;

import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.MeetingType;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.ParticipatedMeetingCursor;
import io.github.smiskinext.meet.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meet.domain.model.valueobject.ShortCodeCollisionException;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.projection.MeetingSearchCriteria;
import io.github.smiskinext.meet.domain.projection.MeetingSortField;
import io.github.smiskinext.meet.domain.projection.MeetingSummary;
import io.github.smiskinext.meet.domain.projection.ParticipatedMeetingSummary;
import io.github.smiskinext.shared.domain.CursorPageResponse;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public class MeetingRepositoryAdapter implements MeetingRepository {

    private final MeetingJpaRepository jpaRepository;

    public MeetingRepositoryAdapter(MeetingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    private static final String SHORT_CODE_CONSTRAINT = "uq_meetings_short_code";
    private static final char LIKE_ESCAPE = '\\';

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
        return jpaRepository.findByIdWithLock(id).map(MeetingPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Meeting> findByShortCode(ShortCode shortCode) {
        return jpaRepository
                .findByShortCode(shortCode.value())
                .map(MeetingPersistenceMapper::toDomain);
    }

    @Override
    public CursorPageResponse<MeetingSummary> searchSummaries(
            MeetingSearchCriteria criteria, int pageSize) {
        Specification<MeetingJpaEntity> specification = searchSpecification(criteria);
        Sort sort = keysetSort(criteria.sort());

        List<MeetingJpaEntity> rows = jpaRepository.findBy(
                specification, query -> query.sortBy(sort).limit(pageSize + 1).all());

        boolean hasNext = rows.size() > pageSize;
        List<MeetingJpaEntity> pageRows = hasNext ? rows.subList(0, pageSize) : rows;

        List<MeetingSummary> summaries =
                pageRows.stream().map(MeetingRepositoryAdapter::toSummary).toList();

        return CursorPageResponse.of(summaries, pageSize, hasNext);
    }

    private static Specification<MeetingJpaEntity> searchSpecification(
            MeetingSearchCriteria criteria) {
        Specification<MeetingJpaEntity> specification = notDeleted();

        if (criteria.creatorId() != null) {
            specification = specification.and(hostIs(criteria.creatorId().value()));
        }

        if (!criteria.statuses().isEmpty()) {
            List<String> statusNames =
                    criteria.statuses().stream().map(MeetingStatus::name).toList();
            specification = specification.and(statusIn(statusNames));
        }

        if (criteria.issueKey() != null && !criteria.issueKey().isBlank()) {
            specification = specification.and(issueKeyIs(criteria.issueKey()));
        }

        if (criteria.search() != null && !criteria.search().isBlank()) {
            specification = specification.and(matchesSearch(criteria.search()));
        }

        MeetingSearchCriteria.Position position = criteria.position();
        if (position != null) {
            specification = specification.and(scrollsPast(criteria.sort(), position));
        }

        return specification;
    }

    private static Specification<MeetingJpaEntity> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    private static Specification<MeetingJpaEntity> hostIs(String hostId) {
        return (root, query, cb) -> cb.equal(root.get("hostId"), hostId);
    }

    private static Specification<MeetingJpaEntity> statusIn(List<String> statusNames) {
        return (root, query, cb) -> root.get("status").in(statusNames);
    }

    private static Specification<MeetingJpaEntity> issueKeyIs(String issueKey) {
        return (root, query, cb) -> cb.equal(root.get("issueKey"), issueKey);
    }

    private static Specification<MeetingJpaEntity> matchesSearch(String search) {
        return (root, query, cb) -> {
            String pattern = "%" + escapeLike(search.toLowerCase(Locale.ROOT)) + "%";
            Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE);
            Predicate issueMatch = cb.like(cb.lower(root.get("issueKey")), pattern, LIKE_ESCAPE);
            return cb.or(titleMatch, issueMatch);
        };
    }

    private static Specification<MeetingJpaEntity> scrollsPast(
            MeetingSortField sort, MeetingSearchCriteria.Position position) {
        return (root, query, cb) -> {
            Expression<Instant> sortValue = sortValueExpression(cb, root, sort);
            Predicate strictlyOlder = cb.lessThan(sortValue, position.sortValue());
            Predicate sameValueLowerId = cb.and(
                    cb.equal(sortValue, position.sortValue()),
                    cb.lessThan(root.get("id"), position.id()));
            return cb.or(strictlyOlder, sameValueLowerId);
        };
    }

    private static Expression<Instant> sortValueExpression(
            CriteriaBuilder cb, Root<MeetingJpaEntity> root, MeetingSortField sort) {
        return switch (sort) {
            case CREATED_AT -> root.get("createdAt");
            case START_TIME -> cb.coalesce(root.get("startTime"), root.get("createdAt"));
        };
    }

    private static Sort keysetSort(MeetingSortField sort) {
        return switch (sort) {
            case CREATED_AT ->
                JpaSort.unsafe(Sort.Direction.DESC, "createdAt")
                        .andUnsafe(Sort.Direction.DESC, "id");
            case START_TIME ->
                JpaSort.unsafe(Sort.Direction.DESC, "coalesce(startTime, createdAt)")
                        .andUnsafe(Sort.Direction.DESC, "id");
        };
    }

    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static MeetingSummary toSummary(MeetingJpaEntity entity) {
        return new MeetingSummary(
                entity.getId(),
                entity.getHostId(),
                entity.getShortCode(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getIssueKey(),
                entity.getStartTime(),
                entity.getEndTime(),
                MeetingType.valueOf(entity.getType()),
                MeetingStatus.valueOf(entity.getStatus()),
                entity.getSettings(),
                entity.getCreatedAt());
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
