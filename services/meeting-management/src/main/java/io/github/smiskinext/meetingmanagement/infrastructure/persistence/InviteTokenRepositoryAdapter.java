package io.github.smiskinext.meetingmanagement.infrastructure.persistence;

import io.github.smiskinext.meetingmanagement.domain.model.InviteToken;
import io.github.smiskinext.meetingmanagement.domain.model.InviteTokenStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteTokenId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteeId;
import io.github.smiskinext.meetingmanagement.domain.port.InviteTokenRepository;
import io.github.phunguy65.zms.shared.domain.valueobject.MeetingId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class InviteTokenRepositoryAdapter implements InviteTokenRepository {

    private final InviteTokenJpaRepository jpa;

    public InviteTokenRepositoryAdapter(InviteTokenJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public InviteToken save(InviteToken token) {
        jpa.save(toEntity(token));
        return token;
    }

    @Override
    public Optional<InviteToken> findById(InviteTokenId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    public List<InviteToken> findByMeetingId(UUID meetingId) {
        return jpa.findByMeetingId(meetingId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<InviteToken> findByMeetingIdAndStatus(UUID meetingId, InviteTokenStatus status) {
        return jpa.findByMeetingIdAndStatus(meetingId, status.name()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public int revokeAllPendingByMeetingId(UUID meetingId) {
        return jpa.updateStatusByMeetingIdAndStatus(
                meetingId, InviteTokenStatus.PENDING.name(), InviteTokenStatus.REVOKED.name());
    }

    @Override
    public int revokeAllPendingByInviteeId(UUID inviteeId) {
        return jpa.updateStatusByInviteeIdAndStatus(
                inviteeId, InviteTokenStatus.PENDING.name(), InviteTokenStatus.REVOKED.name());
    }

    @Override
    public boolean existsByTokenHash(String tokenHash) {
        return jpa.existsByTokenHash(tokenHash);
    }

    @Override
    public Optional<InviteToken> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(this::toDomain);
    }

    private InviteToken toDomain(InviteTokenJpaEntity e) {
        return InviteToken.reconstitute(
                InviteTokenId.of(e.getId()),
                MeetingId.of(e.getMeetingId()),
                InviteeId.of(e.getInviteeId()),
                e.getTokenHash(),
                InviteTokenStatus.valueOf(e.getStatus()),
                e.getExpiresAt(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private InviteTokenJpaEntity toEntity(InviteToken t) {
        return new InviteTokenJpaEntity(
                t.getId().value(),
                t.getMeetingId().value(),
                t.getInviteeId().value(),
                t.getTokenHash(),
                t.getStatus().name(),
                t.getExpiresAt(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}
