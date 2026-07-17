package io.github.smiskinext.meet.infrastructure.persistence;

import io.github.smiskinext.meet.domain.model.MeetingInvitee;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.InviteeId;
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meet.domain.projection.InviteeSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MeetingInviteeRepositoryAdapter implements MeetingInviteeRepository {

    private final MeetingInviteeJpaRepository jpaRepository;

    public MeetingInviteeRepositoryAdapter(MeetingInviteeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<MeetingInvitee> saveAll(List<MeetingInvitee> invitees) {
        List<MeetingInviteeJpaEntity> entities =
                invitees.stream().map(MeetingInviteePersistenceMapper::toEntity).toList();
        jpaRepository.saveAll(entities);
        return invitees;
    }

    @Override
    public MeetingInvitee save(MeetingInvitee invitee) {
        MeetingInviteeJpaEntity entity = MeetingInviteePersistenceMapper.toEntity(invitee);
        jpaRepository.save(entity);
        return invitee;
    }

    /** TODO: Implement in a later slice. */
    @Override
    public Optional<MeetingInvitee> findById(InviteeId id) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    /** TODO: Implement in a later slice. */
    @Override
    public List<MeetingInvitee> findByMeetingId(UUID meetingId) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    /** TODO: Implement in a later slice. */
    @Override
    public Optional<MeetingInvitee> findByMeetingIdAndAccountId(
            UUID meetingId, AccountId accountId) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    /** TODO: Implement in a later slice. */
    @Override
    public List<MeetingInvitee> findPendingByAccountId(AccountId accountId) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    /** TODO: Implement in a later slice. */
    @Override
    public long countActiveByMeetingId(UUID meetingId) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    /** TODO: Implement in a later slice. */
    @Override
    public List<InviteeSummary> findSummariesByMeetingId(UUID meetingId) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }
}
