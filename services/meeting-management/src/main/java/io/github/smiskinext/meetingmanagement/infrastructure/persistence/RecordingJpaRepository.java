package io.github.smiskinext.meetingmanagement.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecordingJpaRepository extends JpaRepository<RecordingJpaEntity, UUID> {

    List<RecordingJpaEntity> findByMeetingId(UUID meetingId);

    List<RecordingJpaEntity> findByMeetingIdAndStatusOrderByCreatedAtDescIdDesc(
            UUID meetingId, String status);

    @Query(
            "SELECT r FROM RecordingJpaEntity r WHERE r.meetingId = :meetingId AND r.status IN :statuses")
    Optional<RecordingJpaEntity> findByMeetingIdAndStatusIn(
            @Param("meetingId") UUID meetingId, @Param("statuses") List<String> statuses);

    /**
     * Lookup for egress webhook handlers: find recording by LiveKit egress ID.
     */
    @Query("SELECT r FROM RecordingJpaEntity r WHERE r.livekitEgressId = :egressId")
    Optional<RecordingJpaEntity> findByLivekitEgressId(@Param("egressId") String egressId);

    @Query("SELECT r FROM RecordingJpaEntity r WHERE r.status = :status AND r.createdAt < :cutoff")
    List<RecordingJpaEntity> findByStatusAndCreatedAtBefore(
            @Param("status") String status, @Param("cutoff") Instant cutoff);

    /**
     * Keyset-scroll query for recordings by meeting, ordered by (created_at DESC, id DESC).
     * Caller should request {@code size + 1} rows to detect next page.
     */
    @Query(
            value = "SELECT * FROM recordings r WHERE r.meeting_id = CAST(:meetingId AS uuid) "
                    + "AND (CAST(:cursorCreatedAt AS timestamptz) IS NULL OR (r.created_at, r.id) < (CAST(:cursorCreatedAt AS timestamptz), CAST(:cursorId AS uuid))) "
                    + "ORDER BY r.created_at DESC, r.id DESC "
                    + "LIMIT :limit",
            nativeQuery = true)
    List<RecordingJpaEntity> findByMeetingIdKeyset(
            @Param("meetingId") String meetingId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit);
}
