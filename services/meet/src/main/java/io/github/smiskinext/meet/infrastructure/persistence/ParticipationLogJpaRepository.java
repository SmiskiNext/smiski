package io.github.smiskinext.meet.infrastructure.persistence;

import io.github.smiskinext.meet.domain.projection.ParticipantSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipationLogJpaRepository
        extends JpaRepository<ParticipationLogJpaEntity, UUID> {

    List<ParticipationLogJpaEntity> findByMeetingId(UUID meetingId);

    long countByMeetingIdAndLeftAtIsNull(UUID meetingId);

    @Query("select new io.github.smiskinext.meet.domain.projection.ParticipantSummary("
            + "p.id, p.meetingId, p.accountId, p.displayName, p.role, p.joinedAt, p.leftAt)"
            + " from ParticipationLogJpaEntity p where p.meetingId = :meetingId")
    List<ParticipantSummary> findParticipantProjectionsByMeetingId(
            @Param("meetingId") UUID meetingId);
}
