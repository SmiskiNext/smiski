package io.github.smiskinext.meet.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingJpaRepository
        extends JpaRepository<MeetingJpaEntity, UUID>, JpaSpecificationExecutor<MeetingJpaEntity> {

    Optional<MeetingJpaEntity> findByShortCode(String shortCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select meeting from MeetingJpaEntity meeting where meeting.id = :id")
    Optional<MeetingJpaEntity> findByIdWithLock(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select meeting from MeetingJpaEntity meeting"
            + " where meeting.id = :id and meeting.deletedAt is null")
    Optional<MeetingJpaEntity> findActiveByIdWithLock(@Param("id") UUID id);

    @Query(
            "select new io.github.smiskinext.meet.infrastructure.persistence.MeetingDetailProjection("
                    + "m.id, m.hostId, m.shortCode, m.type, m.status, m.title, m.description,"
                    + " m.issueId, m.issueKey, m.projectKey, m.settings, m.startTime, m.endTime,"
                    + " m.zoneId, m.organizerEmail, m.organizerDisplayName, m.calendarUid,"
                    + " m.calendarSequence, m.createdAt)"
                    + " from MeetingJpaEntity m where m.id = :id and m.deletedAt is null")
    Optional<MeetingDetailProjection> findDetailById(@Param("id") UUID id);

    /**
     * Finds SCHEDULED meetings whose end time is before the given cutoff, across all tenants,
     * bypassing Hibernate's {@code @TenantId} filter.
     *
     * <p>Returns rows as {@code (id, tenant_id)} pairs. Uses {@code FOR UPDATE SKIP LOCKED}
     * to prevent concurrent job executions from processing the same row twice.
     *
     * @param batchSize maximum number of rows to return
     * @param cutoff    upper bound for {@code end_time}; only meetings that ended before this instant are returned
     * @return list of {@code Object[]} rows where {@code [0]} is the meeting id (UUID) and
     *     {@code [1]} is the tenant id (String)
     */
    @Query(
            value = "SELECT id, tenant_id FROM meetings"
                    + " WHERE status = 'SCHEDULED' AND end_time < :cutoff AND deleted_at IS NULL"
                    + " LIMIT :batchSize FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<Object[]> findScheduledExpiredIdsAcrossTenants(
            @Param("batchSize") int batchSize, @Param("cutoff") Instant cutoff);
}
