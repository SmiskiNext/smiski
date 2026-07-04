package io.github.smiskinext.meetingmanagement.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingJpaRepository extends JpaRepository<MeetingJpaEntity, UUID> {

    Optional<MeetingJpaEntity> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MeetingJpaEntity m WHERE m.id = :id")
    Optional<MeetingJpaEntity> findByIdWithLock(@Param("id") UUID id);

    /**
     * Keyset-scroll query for meetings by host, ordered by (created_at DESC, id DESC).
     * Caller should request {@code size + 1} rows to detect next page.
     */
    @Query(
            value = "SELECT * FROM meetings m WHERE m.host_id = CAST(:hostId AS uuid) "
                    + "AND (CAST(:cursorCreatedAt AS timestamptz) IS NULL OR (m.created_at, m.id) < (CAST(:cursorCreatedAt AS timestamptz), CAST(:cursorId AS uuid))) "
                    + "ORDER BY m.created_at DESC, m.id DESC "
                    + "LIMIT :limit",
            nativeQuery = true)
    List<MeetingJpaEntity> findByHostIdKeyset(
            @Param("hostId") String hostId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit);

    @Query(
            value =
                    "SELECT m.id AS id, m.host_id AS hostId, m.short_code AS shortCode, m.title AS title, "
                            + "m.description AS description, m.start_time AS startTime, m.end_time AS endTime, "
                            + "m.type AS type, m.status AS status, m.settings::text AS settings, "
                            + "m.created_at AS createdAt, pm.last_joined_at AS lastJoinedAt "
                            + "FROM meetings m "
                            + "JOIN (SELECT pl.meeting_id, MAX(pl.joined_at) AS last_joined_at "
                            + "      FROM participation_logs pl "
                            + "      WHERE pl.user_id = CAST(:userId AS uuid) "
                            + "      GROUP BY pl.meeting_id) pm ON pm.meeting_id = m.id "
                            + "WHERE (CAST(:cursorJoinedAt AS timestamptz) IS NULL "
                            + "       OR (pm.last_joined_at, m.id) < (CAST(:cursorJoinedAt AS timestamptz), CAST(:cursorMeetingId AS uuid))) "
                            + "ORDER BY pm.last_joined_at DESC, m.id DESC "
                            + "LIMIT :limit",
            nativeQuery = true)
    List<ParticipatedMeetingRow> findParticipatedMeetingsKeyset(
            @Param("userId") String userId,
            @Param("cursorJoinedAt") Instant cursorJoinedAt,
            @Param("cursorMeetingId") String cursorMeetingId,
            @Param("limit") int limit);

    @Query(
            value =
                    "SELECT m.id AS id, m.host_id AS hostId, m.short_code AS shortCode, m.title AS title, "
                            + "m.description AS description, m.start_time AS startTime, m.end_time AS endTime, "
                            + "m.type AS type, m.status AS status, m.settings::text AS settings, "
                            + "m.created_at AS createdAt, pm.last_joined_at AS lastJoinedAt "
                            + "FROM meetings m "
                            + "JOIN (SELECT pl.meeting_id, MAX(pl.joined_at) AS last_joined_at "
                            + "      FROM participation_logs pl "
                            + "      WHERE pl.user_id = CAST(:userId AS uuid) "
                            + "      GROUP BY pl.meeting_id) pm ON pm.meeting_id = m.id "
                            + "WHERE m.status IN (:statuses) "
                            + "  AND (CAST(:cursorJoinedAt AS timestamptz) IS NULL "
                            + "       OR (pm.last_joined_at, m.id) < (CAST(:cursorJoinedAt AS timestamptz), CAST(:cursorMeetingId AS uuid))) "
                            + "ORDER BY pm.last_joined_at DESC, m.id DESC "
                            + "LIMIT :limit",
            nativeQuery = true)
    List<ParticipatedMeetingRow> findParticipatedMeetingsKeysetByStatuses(
            @Param("userId") String userId,
            @Param("statuses") List<String> statuses,
            @Param("cursorJoinedAt") Instant cursorJoinedAt,
            @Param("cursorMeetingId") String cursorMeetingId,
            @Param("limit") int limit);
}
