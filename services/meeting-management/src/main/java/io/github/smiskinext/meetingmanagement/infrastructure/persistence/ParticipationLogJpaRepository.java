package io.github.smiskinext.meetingmanagement.infrastructure.persistence;

import io.github.smiskinext.meetingmanagement.domain.projection.ParticipantSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipationLogJpaRepository
        extends JpaRepository<ParticipationLogJpaEntity, Long> {
    @Query("SELECT COUNT(p) FROM ParticipationLogJpaEntity p "
            + "WHERE p.meetingId = :meetingId AND p.leftAt IS NULL")
    long countActiveByMeetingId(@Param("meetingId") UUID meetingId);

    /**
     * Primary lookup for participant_left webhook: find active session by LiveKit SID.
     */
    @Query("SELECT p FROM ParticipationLogJpaEntity p "
            + "WHERE p.livekitParticipantSid = :sid AND p.leftAt IS NULL")
    Optional<ParticipationLogJpaEntity> findActiveBySid(@Param("sid") String sid);

    /**
     * Lookup for participant_joined webhook: find pending entry to assign SID.
     */
    @Query("SELECT p FROM ParticipationLogJpaEntity p "
            + "WHERE p.meetingId = :meetingId AND p.livekitIdentity = :identity AND p.leftAt IS NULL "
            + "ORDER BY p.joinedAt DESC")
    Optional<ParticipationLogJpaEntity> findActiveByMeetingIdAndIdentity(
            @Param("meetingId") UUID meetingId, @Param("identity") String identity);

    /**
     * Bulk lookup for room_finished / EndMeeting: all active sessions for a meeting.
     */
    @Query("SELECT p FROM ParticipationLogJpaEntity p "
            + "WHERE p.meetingId = :meetingId AND p.leftAt IS NULL")
    List<ParticipationLogJpaEntity> findActiveByMeetingId(@Param("meetingId") UUID meetingId);

    @Query("SELECT p FROM ParticipationLogJpaEntity p "
            + "WHERE p.userId = :userId AND p.leftAt IS NULL")
    List<ParticipationLogJpaEntity> findActiveByUserId(@Param("userId") UUID userId);

    @Query("SELECT p FROM ParticipationLogJpaEntity p "
            + "WHERE p.meetingId = :meetingId AND p.userId = :userId AND p.leftAt IS NULL")
    List<ParticipationLogJpaEntity> findActiveByMeetingIdAndUserId(
            @Param("meetingId") UUID meetingId, @Param("userId") UUID userId);

    @Query("SELECT p FROM ParticipationLogJpaEntity p "
            + "WHERE p.meetingId = :meetingId AND p.displayName = :displayName AND p.leftAt IS NULL "
            + "AND p.userId IS NULL")
    List<ParticipationLogJpaEntity> findActiveByMeetingIdAndDisplayName(
            @Param("meetingId") UUID meetingId, @Param("displayName") String displayName);

    @Query("SELECT new io.github.phunguy65.zms.meetingmanagement.domain.projection"
            + ".ParticipantSummary("
            + "p.id, p.meetingId, p.userId, p.displayName, p.role, "
            + "p.joinedAt, p.leftAt) "
            + "FROM ParticipationLogJpaEntity p "
            + "WHERE p.meetingId = :meetingId "
            + "ORDER BY p.joinedAt DESC, p.id DESC")
    List<ParticipantSummary> findParticipantSummariesByMeetingId(
            @Param("meetingId") UUID meetingId);

    boolean existsByMeetingIdAndUserId(UUID meetingId, UUID userId);

    @Query(
            value = "SELECT * FROM ("
                    + "  SELECT DISTINCT ON (p.user_id) p.* FROM participation_logs p "
                    + "  WHERE p.meeting_id = CAST(:meetingId AS uuid) AND p.user_id IS NOT NULL "
                    + "  ORDER BY p.user_id, p.joined_at DESC, p.id DESC"
                    + ") registered "
                    + "UNION ALL "
                    + "SELECT * FROM ("
                    + "  SELECT DISTINCT ON (p.display_name) p.* FROM participation_logs p "
                    + "  WHERE p.meeting_id = CAST(:meetingId AS uuid) AND p.user_id IS NULL "
                    + "  ORDER BY p.display_name, p.joined_at DESC, p.id DESC"
                    + ") guests "
                    + "ORDER BY joined_at DESC, id DESC",
            nativeQuery = true)
    List<ParticipationLogJpaEntity> findDistinctSessionsByMeetingId(
            @Param("meetingId") UUID meetingId);
}
