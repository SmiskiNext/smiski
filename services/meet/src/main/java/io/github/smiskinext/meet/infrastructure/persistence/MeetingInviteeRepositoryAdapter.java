package io.github.smiskinext.meet.infrastructure.persistence;

import io.github.smiskinext.meet.domain.model.InviteeStatus;
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

    @Override
    public Optional<MeetingInvitee> findById(InviteeId id) {
        return jpaRepository.findById(id.value()).map(MeetingInviteePersistenceMapper::toDomain);
    }

    @Override
    public List<MeetingInvitee> findByMeetingId(UUID meetingId) {
        return jpaRepository.findByMeetingIdAndRemovedAtIsNull(meetingId).stream()
                .map(MeetingInviteePersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<MeetingInvitee> findByMeetingIdAndAccountId(
            UUID meetingId, AccountId accountId) {
        return jpaRepository
                .findByMeetingIdAndAccountIdAndRemovedAtIsNull(meetingId, accountId.value())
                .map(MeetingInviteePersistenceMapper::toDomain);
    }

    @Override
    public List<MeetingInvitee> findPendingByAccountId(AccountId accountId) {
        return jpaRepository
                .findByAccountIdAndStatusAndRemovedAtIsNull(
                        accountId.value(), InviteeStatus.NEEDS_ACTION.name())
                .stream()
                .map(MeetingInviteePersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public long countActiveByMeetingId(UUID meetingId) {
        return jpaRepository.countByMeetingIdAndStatusInAndRemovedAtIsNull(
                meetingId,
                List.of(
                        InviteeStatus.NEEDS_ACTION.name(),
                        InviteeStatus.ACCEPTED.name(),
                        InviteeStatus.TENTATIVE.name()));
    }

    @Override
    public List<InviteeSummary> findSummariesByMeetingId(UUID meetingId) {
        return findByMeetingId(meetingId).stream()
                .map(invitee -> new InviteeSummary(
                        invitee.getAccountId().value(),
                        invitee.getEmail().value(),
                        invitee.getDisplayName().value(),
                        invitee.getStatus().name(),
                        invitee.getInvitedAt(),
                        invitee.getRespondedAt().orElse(null)))
                .toList();
    }
}
