package io.github.smiskinext.meet.infrastructure.persistence;

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
                invitee.getRemovedAt().orElse(null));
    }

    static MeetingInvitee toDomain(MeetingInviteeJpaEntity entity) {
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
                entity.getRemovedAt());
    }
}
