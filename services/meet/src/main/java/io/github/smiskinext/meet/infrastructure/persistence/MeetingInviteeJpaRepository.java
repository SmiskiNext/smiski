package io.github.smiskinext.meet.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingInviteeJpaRepository extends JpaRepository<MeetingInviteeJpaEntity, UUID> {
    List<MeetingInviteeJpaEntity> findByMeetingIdAndRemovedAtIsNull(UUID meetingId);

    Optional<MeetingInviteeJpaEntity> findByMeetingIdAndAccountIdAndRemovedAtIsNull(
            UUID meetingId, String accountId);

    List<MeetingInviteeJpaEntity> findByAccountIdAndStatusAndRemovedAtIsNull(
            String accountId, String status);

    long countByMeetingIdAndStatusInAndRemovedAtIsNull(UUID meetingId, Collection<String> statuses);
}
