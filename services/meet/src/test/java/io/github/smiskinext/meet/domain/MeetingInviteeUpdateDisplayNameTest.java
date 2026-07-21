package io.github.smiskinext.meet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meet.domain.model.InviteeRole;
import io.github.smiskinext.meet.domain.model.MeetingInvitee;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.Email;
import io.github.smiskinext.meet.domain.model.valueobject.InviteeDisplayName;
import io.github.smiskinext.meet.domain.model.valueobject.InviterId;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeetingInviteeUpdateDisplayNameTest {

    @Test
    void updateDisplayNameChangesNameWhenDifferent() {
        MeetingInvitee invitee = invitee("Old Name");

        var result = invitee.updateDisplayName(InviteeDisplayName.of("New Name"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(invitee.getDisplayName().value()).isEqualTo("New Name");
    }

    @Test
    void updateDisplayNameIsNoOpWhenUnchanged() {
        MeetingInvitee invitee = invitee("Same Name");

        var result = invitee.updateDisplayName(InviteeDisplayName.of("Same Name"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(invitee.getDisplayName().value()).isEqualTo("Same Name");
    }

    @Test
    void updateDisplayNameIsRejectedWhenRemoved() {
        MeetingInvitee invitee = invitee("Old Name");
        invitee.remove();

        var result = invitee.updateDisplayName(InviteeDisplayName.of("New Name"));

        assertThat(result.isFailure()).isTrue();
        assertThat(invitee.getDisplayName().value()).isEqualTo("Old Name");
    }

    private MeetingInvitee invitee(String displayName) {
        return MeetingInvitee.create(
                TenantId.of("tenant"),
                MeetingId.of(UUID.randomUUID()),
                InviterId.of("host"),
                AccountId.of("account"),
                Email.of("invitee@example.com"),
                InviteeDisplayName.of(displayName),
                InviteeRole.REQ_PARTICIPANT,
                true);
    }
}
