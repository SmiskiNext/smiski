package io.github.smiskinext.meetingmanagement.infrastructure.persistence;

import io.github.smiskinext.meetingmanagement.domain.model.InviteeStatus;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingInvitee;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteTokenId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteeDisplayName;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteeId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviterId;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meetingmanagement.domain.projection.InviteeSummary;
import io.github.smiskinext.shared.domain.valueobject.Email;
import io.github.smiskinext.shared.domain.valueobject.MeetingId;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MeetingInviteeRepositoryAdapter implements MeetingInviteeRepository {

    private final MeetingInviteeJpaRepository jpa;

    public MeetingInviteeRepositoryAdapter(MeetingInviteeJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<MeetingInvitee> saveAll(List<MeetingInvitee> invitees) {
        List<MeetingInviteeJpaEntity> entities =
                invitees.stream().map(this::toEntity).toList();
        jpa.saveAll(entities);
        return invitees;
    }

    @Override
    public MeetingInvitee save(MeetingInvitee invitee) {
        jpa.save(toEntity(invitee));
        return invitee;
    }

    @Override
    public Optional<MeetingInvitee> findById(InviteeId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    public List<MeetingInvitee> findByMeetingId(UUID meetingId) {
        return jpa.findByMeetingId(meetingId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<MeetingInvitee> findByMeetingIdAndUserId(UUID meetingId, UUID userId) {
        return jpa.findByMeetingIdAndUserId(meetingId, userId).map(this::toDomain);
    }

    @Override
    public List<MeetingInvitee> findPendingByUserId(UUID userId) {
        return jpa.findPendingByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public long countActiveByMeetingId(UUID meetingId) {
        return jpa.countByMeetingIdAndStatusIn(
                meetingId, List.of(InviteeStatus.PENDING.name(), InviteeStatus.ACCEPTED.name()));
    }

    @Override
    public List<InviteeSummary> findSummariesByMeetingId(UUID meetingId) {
        return jpa.findSummariesByMeetingId(meetingId);
    }

    private MeetingInvitee toDomain(MeetingInviteeJpaEntity e) {
        return MeetingInvitee.reconstitute(
                InviteeId.of(e.getId()),
                MeetingId.of(e.getMeetingId()),
                InviterId.of(e.getInviterId()),
                e.getUserId() != null ? UserId.of(e.getUserId()) : null,
                Email.of(e.getEmail()),
                e.getDisplayName() != null ? InviteeDisplayName.of(e.getDisplayName()) : null,
                InviteeStatus.valueOf(e.getStatus()),
                e.getInvitedAt(),
                e.getRespondedAt(),
                e.getInviteTokenId() != null ? InviteTokenId.of(e.getInviteTokenId()) : null);
    }

    private MeetingInviteeJpaEntity toEntity(MeetingInvitee i) {
        return new MeetingInviteeJpaEntity(
                i.getId().value(),
                i.getMeetingId().value(),
                i.getInviterId().value(),
                i.getUserId().map(UserId::value).orElse(null),
                i.getEmail().value(),
                i.getDisplayName().map(InviteeDisplayName::value).orElse(null),
                i.getStatus().name(),
                i.getInvitedAt(),
                i.getRespondedAt().orElse(null),
                i.getInviteTokenId().map(InviteTokenId::value).orElse(null));
    }
}
