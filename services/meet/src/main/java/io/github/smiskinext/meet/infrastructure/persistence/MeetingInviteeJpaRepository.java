package io.github.smiskinext.meet.infrastructure.persistence;

import io.github.smiskinext.meet.domain.projection.InviteeSummary;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingInviteeJpaRepository extends JpaRepository<MeetingInviteeJpaEntity, UUID> {
    List<MeetingInviteeJpaEntity> findByMeetingIdAndRemovedAtIsNull(UUID meetingId);

    @Query("select new io.github.smiskinext.meet.domain.projection.InviteeSummary("
            + "i.id, i.accountId, i.email, i.displayName, i.status, i.invitedAt, i.respondedAt)"
            + " from MeetingInviteeJpaEntity i"
            + " where i.meetingId = :meetingId and i.removedAt is null")
    List<InviteeSummary> findSummaryProjectionsByMeetingId(@Param("meetingId") UUID meetingId);

    Optional<MeetingInviteeJpaEntity> findByMeetingIdAndAccountIdAndRemovedAtIsNull(
            UUID meetingId, String accountId);

    List<MeetingInviteeJpaEntity> findByAccountIdAndStatusAndRemovedAtIsNull(
            String accountId, String status);

    long countByMeetingIdAndStatusInAndRemovedAtIsNull(UUID meetingId, Collection<String> statuses);
}
