package io.github.smiskinext.meet.infrastructure.persistence;

import io.github.smiskinext.meet.domain.model.InviteTokenStatus;
import io.github.smiskinext.meet.domain.model.InviteeRole;
import io.github.smiskinext.meet.domain.model.InviteeStatus;
import io.github.smiskinext.meet.domain.model.MeetingInvitee;
import io.github.smiskinext.meet.domain.model.valueobject.*;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;

/**
 * Maps between {@link MeetingInviteeJpaEntity} and the {@link MeetingInvitee} domain aggregate.
 */
final class MeetingInviteePersistenceMapper {

    private MeetingInviteePersistenceMapper() {}

    static MeetingInviteeJpaEntity toEntity(MeetingInvitee invitee) {
        InviteToken token = invitee.getInviteToken().orElse(null);
        return new MeetingInviteeJpaEntity(
                invitee.getId().value(),
                invitee.getMeetingId().value(),
                invitee.getInviterId().value(),
                invitee.getAccountId().value(),
                invitee.getEmail().value(),
                invitee.getDisplayName().value(),
                invitee.getRole().name(),
                invitee.isRsvp(),
                invitee.getStatus().name(),
                invitee.getInvitedAt(),
                invitee.getRespondedAt().orElse(null),
                invitee.getRemovedAt().orElse(null),
                token != null ? token.tokenHash() : null,
                token != null ? token.status().name() : null,
                token != null ? token.expiresAt() : null,
                token != null ? token.createdAt() : null,
                token != null ? token.updatedAt() : null);
    }

    static MeetingInvitee toDomain(MeetingInviteeJpaEntity entity) {
        InviteToken token = null;
        if (entity.getTokenHash() != null && entity.getTokenStatus() != null) {
            token = InviteToken.reconstitute(
                    entity.getTokenHash(),
                    InviteTokenStatus.valueOf(entity.getTokenStatus()),
                    entity.getTokenExpiresAt(),
                    entity.getTokenCreatedAt(),
                    entity.getTokenUpdatedAt());
        }

        return MeetingInvitee.reconstitute(
                TenantId.of(
                        entity.getTenantId() != null
                                ? entity.getTenantId()
                                : TenantContext.getCurrentTenant()),
                InviteeId.of(entity.getId()),
                MeetingId.of(entity.getMeetingId()),
                InviterId.of(entity.getInviterId()),
                AccountId.of(entity.getAccountId()),
                Email.of(entity.getEmail()),
                InviteeDisplayName.of(entity.getDisplayName()),
                InviteeRole.valueOf(entity.getRole()),
                entity.isRsvp(),
                InviteeStatus.valueOf(entity.getStatus()),
                entity.getInvitedAt(),
                entity.getRespondedAt(),
                entity.getRemovedAt(),
                token);
    }
}
