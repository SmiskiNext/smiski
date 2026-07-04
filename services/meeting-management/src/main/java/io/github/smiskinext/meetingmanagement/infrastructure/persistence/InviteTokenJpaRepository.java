package io.github.smiskinext.meetingmanagement.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InviteTokenJpaRepository extends JpaRepository<InviteTokenJpaEntity, UUID> {

    List<InviteTokenJpaEntity> findByMeetingId(UUID meetingId);

    List<InviteTokenJpaEntity> findByMeetingIdAndStatus(UUID meetingId, String status);

    Optional<InviteTokenJpaEntity> findByTokenHash(String tokenHash);

    boolean existsByTokenHash(String tokenHash);

    @Modifying
    @Query(
            "UPDATE InviteTokenJpaEntity t SET t.status = :newStatus, t.updatedAt = CURRENT_TIMESTAMP "
                    + "WHERE t.meetingId = :meetingId AND t.status = :currentStatus")
    int updateStatusByMeetingIdAndStatus(
            @Param("meetingId") UUID meetingId,
            @Param("currentStatus") String currentStatus,
            @Param("newStatus") String newStatus);

    @Modifying
    @Query(
            "UPDATE InviteTokenJpaEntity t SET t.status = :newStatus, t.updatedAt = CURRENT_TIMESTAMP "
                    + "WHERE t.inviteeId = :inviteeId AND t.status = :currentStatus")
    int updateStatusByInviteeIdAndStatus(
            @Param("inviteeId") UUID inviteeId,
            @Param("currentStatus") String currentStatus,
            @Param("newStatus") String newStatus);
}
