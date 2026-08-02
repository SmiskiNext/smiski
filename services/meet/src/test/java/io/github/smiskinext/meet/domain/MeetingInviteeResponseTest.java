package io.github.smiskinext.meet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meet.domain.event.InviteeAcceptedEvent;
import io.github.smiskinext.meet.domain.event.InviteeDeclinedEvent;
import io.github.smiskinext.meet.domain.event.InviteeTentativeEvent;
import io.github.smiskinext.meet.domain.model.InviteeRole;
import io.github.smiskinext.meet.domain.model.InviteeStatus;
import io.github.smiskinext.meet.domain.model.MeetingContext;
import io.github.smiskinext.meet.domain.model.MeetingInvitee;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.Email;
import io.github.smiskinext.meet.domain.model.valueobject.InviteeDisplayName;
import io.github.smiskinext.meet.domain.model.valueobject.InviterId;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.shared.domain.DomainEvent;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeetingInviteeResponseTest {

    private static final Instant START = Instant.now().plus(1, ChronoUnit.HOURS);
    private static final Instant END = START.plus(1, ChronoUnit.HOURS);

    @Test
    void tentativeFromNeedsActionSetsStatusAndRegistersEnrichedEvent() {
        MeetingInvitee invitee = needsActionInvitee();

        assertThat(invitee.tentative(context()).isSuccess()).isTrue();

        assertThat(invitee.getStatus()).isEqualTo(InviteeStatus.TENTATIVE);
        assertThat(invitee.getRespondedAt()).isPresent();
        InviteeTentativeEvent event = event(invitee, InviteeTentativeEvent.class);
        assertThat(event.status()).isEqualTo("TENTATIVE");
        assertThat(event.meetingTitle()).isEqualTo("Sprint Review");
        assertThat(event.startTime()).isEqualTo(START);
        assertThat(event.endTime()).isEqualTo(END);
        assertThat(event.zoneId()).isEqualTo("UTC");
        assertThat(event.organizerEmail()).isEqualTo("host@example.com");
        assertThat(event.organizerDisplayName()).isEqualTo("Host User");
        assertThat(event.inviteeDisplayName()).isEqualTo("Invitee");
        assertThat(event.calendarUid()).isEqualTo("uid-42");
        assertThat(event.calendarSequence()).isEqualTo(3);
    }

    @Test
    void tentativeToAcceptedIsPermitted() {
        MeetingInvitee invitee = needsActionInvitee();
        invitee.tentative(context());

        assertThat(invitee.accept(context()).isSuccess()).isTrue();
        assertThat(invitee.getStatus()).isEqualTo(InviteeStatus.ACCEPTED);
    }

    @Test
    void declinedInvitationRejectsAnyFurtherResponse() {
        MeetingInvitee invitee = needsActionInvitee();
        invitee.decline(context());

        assertThat(invitee.accept(context()).isFailure()).isTrue();
        assertThat(invitee.tentative(context()).isFailure()).isTrue();
        assertThat(invitee.decline(context()).isFailure()).isTrue();
        assertThat(invitee.getStatus()).isEqualTo(InviteeStatus.DECLINED);
    }

    @Test
    void removedInvitationRejectsTentative() {
        MeetingInvitee invitee = needsActionInvitee();
        invitee.remove();

        assertThat(invitee.tentative(context()).isFailure()).isTrue();
        assertThat(invitee.getStatus()).isEqualTo(InviteeStatus.NEEDS_ACTION);
    }

    @Test
    void acceptRegistersEnrichedAcceptedEvent() {
        MeetingInvitee invitee = needsActionInvitee();

        assertThat(invitee.accept(context()).isSuccess()).isTrue();
        InviteeAcceptedEvent event = event(invitee, InviteeAcceptedEvent.class);
        assertThat(event.status()).isEqualTo("ACCEPTED");
        assertThat(event.calendarUid()).isEqualTo("uid-42");
        assertThat(event.inviteeDisplayName()).isEqualTo("Invitee");
    }

    @Test
    void declineRegistersEnrichedDeclinedEvent() {
        MeetingInvitee invitee = needsActionInvitee();

        assertThat(invitee.decline(context()).isSuccess()).isTrue();
        InviteeDeclinedEvent event = event(invitee, InviteeDeclinedEvent.class);
        assertThat(event.status()).isEqualTo("DECLINED");
        assertThat(event.calendarSequence()).isEqualTo(3);
    }

    private static MeetingInvitee needsActionInvitee() {
        return MeetingInvitee.create(
                TenantId.of("tenant"),
                MeetingId.of(UUID.randomUUID()),
                InviterId.of("host"),
                AccountId.of("account"),
                Email.of("invitee@example.com"),
                InviteeDisplayName.of("Invitee"),
                InviteeRole.REQ_PARTICIPANT,
                true);
    }

    private static MeetingContext context() {
        return new MeetingContext(
                "Sprint Review",
                START,
                END,
                "UTC",
                "host@example.com",
                "Host User",
                "uid-42",
                3,
                "issue-1",
                "PROJ-1",
                "PROJ",
                "ABC123");
    }

    @SuppressWarnings("unchecked")
    private static <T extends DomainEvent> T event(MeetingInvitee invitee, Class<T> type) {
        return (T) invitee.getDomainEvents().stream()
                .filter(type::isInstance)
                .findFirst()
                .orElseThrow();
    }
}
