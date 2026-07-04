package io.github.smiskinext.meetingmanagement.infrastructure.persistence;

import io.github.smiskinext.meetingmanagement.domain.projection.InviteeSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingInviteeJpaRepository extends JpaRepository<MeetingInviteeJpaEntity, UUID> {

    List<MeetingInviteeJpaEntity> findByMeetingId(UUID meetingId);

    Optional<MeetingInviteeJpaEntity> findByMeetingIdAndUserId(UUID meetingId, UUID userId);

    @Query("SELECT i FROM MeetingInviteeJpaEntity i JOIN MeetingJpaEntity m ON m.id = i.meetingId "
            + "WHERE i.userId = :userId AND i.status = 'PENDING' "
            + "ORDER BY i.invitedAt DESC, i.id DESC")
    List<MeetingInviteeJpaEntity> findPendingByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(i) FROM MeetingInviteeJpaEntity i "
            + "WHERE i.meetingId = :meetingId AND i.status IN :statuses")
    long countByMeetingIdAndStatusIn(
            @Param("meetingId") UUID meetingId, @Param("statuses") List<String> statuses);

    @Query("SELECT new io.github.phunguy65.zms.meetingmanagement.domain.projection.InviteeSummary("
            + "i.userId, i.email, i.displayName, i.status, i.invitedAt, i.respondedAt) "
            + "FROM MeetingInviteeJpaEntity i "
            + "WHERE i.meetingId = :meetingId "
            + "ORDER BY i.invitedAt DESC, i.id DESC")
    List<InviteeSummary> findSummariesByMeetingId(@Param("meetingId") UUID meetingId);
}
