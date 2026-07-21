package io.github.smiskinext.meet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meet.domain.model.InviteeRole;
import io.github.smiskinext.meet.domain.model.InviteeStatus;
import io.github.smiskinext.meet.domain.model.MeetingInvitee;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.Email;
import io.github.smiskinext.meet.domain.model.valueobject.InviteeDisplayName;
import io.github.smiskinext.meet.domain.model.valueobject.InviterId;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeetingInviteeRemovalTest {

    @Test
    void removeIsIdempotentAndPreservesInviteeIdentityAndStatus() {
        MeetingInvitee invitee = MeetingInvitee.create(
                TenantId.of("tenant"),
                MeetingId.of(UUID.randomUUID()),
                InviterId.of("host"),
                AccountId.of("account"),
                Email.of("invitee@example.com"),
                InviteeDisplayName.of("Invitee"),
                InviteeRole.REQ_PARTICIPANT,
                true);

        invitee.remove();
        var removedAt = invitee.getRemovedAt().orElseThrow();
        invitee.remove();

        assertThat(invitee.getRemovedAt()).hasValue(removedAt);
        assertThat(invitee.getId()).isNotNull();
        assertThat(invitee.getStatus()).isEqualTo(InviteeStatus.NEEDS_ACTION);
        assertThat(invitee.accept().isFailure()).isTrue();
    }
}
