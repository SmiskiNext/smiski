package io.github.smiskinext.meetingmanagement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteeId;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.MeetingId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InviteTokenTest {

    private static final MeetingId MEETING_ID = MeetingId.of(UUID.randomUUID());
    private static final InviteeId INVITEE_ID = InviteeId.of(UUID.randomUUID());
    private static final String TOKEN_HASH = "abc123hash";
    private static final Instant FUTURE = Instant.now().plus(7, ChronoUnit.DAYS);
    private static final Instant PAST = Instant.now().minus(1, ChronoUnit.SECONDS);

    @Test
    void create_returnsSuccessWithPendingStatus() {
        var result = InviteToken.create(MEETING_ID, INVITEE_ID, TOKEN_HASH, FUTURE);

        assertThat(result.isSuccess()).isTrue();
        InviteToken token = ((Result.Success<InviteToken, MeetingError>) result).value();
        assertThat(token.getStatus()).isEqualTo(InviteTokenStatus.PENDING);
        assertThat(token.getMeetingId()).isEqualTo(MEETING_ID);
        assertThat(token.getInviteeId()).isEqualTo(INVITEE_ID);
        assertThat(token.getTokenHash()).isEqualTo(TOKEN_HASH);
        assertThat(token.getExpiresAt()).isEqualTo(FUTURE);
    }

    @Test
    void create_failsWhenExpiresAtIsInThePast() {
        var result = InviteToken.create(MEETING_ID, INVITEE_ID, TOKEN_HASH, PAST);

        assertThat(result.isFailure()).isTrue();
        MeetingError error = ((Result.Failure<InviteToken, MeetingError>) result).error();
        assertThat(error).isInstanceOf(MeetingError.InvalidSettings.class);
    }

    @Test
    void markUsed_fromPendingSucceeds() {
        InviteToken token = pendingToken();

        var result = token.markUsed();

        assertThat(result.isSuccess()).isTrue();
        assertThat(token.getStatus()).isEqualTo(InviteTokenStatus.USED);
    }

    @Test
    void markUsed_fromRevokedFails() {
        InviteToken token = pendingToken();
        token.revoke();

        var result = token.markUsed();

        assertThat(result.isFailure()).isTrue();
        assertThat(token.getStatus()).isEqualTo(InviteTokenStatus.REVOKED);
    }

    @Test
    void revoke_fromPendingSucceeds() {
        InviteToken token = pendingToken();

        var result = token.revoke();

        assertThat(result.isSuccess()).isTrue();
        assertThat(token.getStatus()).isEqualTo(InviteTokenStatus.REVOKED);
    }

    @Test
    void revoke_fromUsedFails() {
        InviteToken token = pendingToken();
        token.markUsed();

        var result = token.revoke();

        assertThat(result.isFailure()).isTrue();
        assertThat(token.getStatus()).isEqualTo(InviteTokenStatus.USED);
    }

    @Test
    void revoke_fromRevokedFails() {
        InviteToken token = pendingToken();
        token.revoke();

        var result = token.revoke();

        assertThat(result.isFailure()).isTrue();
    }

    @Test
    void statusTransition_expiredCannotTransition() {
        assertThat(InviteTokenStatus.EXPIRED.canTransitionTo(InviteTokenStatus.USED))
                .isFalse();
        assertThat(InviteTokenStatus.EXPIRED.canTransitionTo(InviteTokenStatus.REVOKED))
                .isFalse();
    }

    @Test
    void statusTransition_pendingCanTransitionToUsedOrRevoked() {
        assertThat(InviteTokenStatus.PENDING.canTransitionTo(InviteTokenStatus.USED))
                .isTrue();
        assertThat(InviteTokenStatus.PENDING.canTransitionTo(InviteTokenStatus.REVOKED))
                .isTrue();
        assertThat(InviteTokenStatus.PENDING.canTransitionTo(InviteTokenStatus.EXPIRED))
                .isFalse();
        assertThat(InviteTokenStatus.PENDING.canTransitionTo(InviteTokenStatus.PENDING))
                .isFalse();
    }

    private static InviteToken pendingToken() {
        var result = InviteToken.create(MEETING_ID, INVITEE_ID, TOKEN_HASH, FUTURE);
        return ((Result.Success<InviteToken, MeetingError>) result).value();
    }
}
