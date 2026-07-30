package io.github.smiskinext.meet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meet.domain.event.MeetingCanceledEvent;
import io.github.smiskinext.meet.domain.model.CancelReason;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.MeetingType;
import io.github.smiskinext.meet.domain.model.valueobject.*;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeetingCancelTest {

    @Test
    void scheduledMeetingCanceledWithHostCanceledReasonRegistersExactlyOneEvent() {
        Meeting meeting = scheduledMeeting();

        Result<Void, MeetingError> result = meeting.cancel(CancelReason.HOST_CANCELED);

        assertThat(result.isSuccess()).isTrue();
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.CANCELED);
        assertThat(meeting.getCancelReason()).contains(CancelReason.HOST_CANCELED);
        assertThat(meeting.getDomainEvents())
                .singleElement()
                .isInstanceOf(MeetingCanceledEvent.class);
    }

    @Test
    void scheduledMeetingCanceledWithNoShowReasonRegistersExactlyOneEvent() {
        Meeting meeting = scheduledMeeting();

        Result<Void, MeetingError> result = meeting.cancel(CancelReason.NO_SHOW);

        assertThat(result.isSuccess()).isTrue();
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.CANCELED);
        assertThat(meeting.getCancelReason()).contains(CancelReason.NO_SHOW);
        assertThat(meeting.getDomainEvents())
                .singleElement()
                .isInstanceOf(MeetingCanceledEvent.class);
    }

    @Test
    void cancelWithInviteesPropagatesToEventPayload() {
        Meeting meeting = scheduledMeeting();
        List<MeetingCanceledEvent.InviteeInfo> invitees = List.of(
                new MeetingCanceledEvent.InviteeInfo(
                        "account-1", "alice@example.com", "Alice", "ACCEPTED", Instant.now()),
                new MeetingCanceledEvent.InviteeInfo(
                        "account-2", "bob@example.com", "Bob", "NEEDS_ACTION", Instant.now()));

        meeting.cancel(
                CancelReason.HOST_CANCELED,
                "Title",
                "abc-short-code",
                Instant.now().plus(1, ChronoUnit.HOURS),
                invitees);

        assertThat(meeting.getDomainEvents())
                .singleElement()
                .isInstanceOfSatisfying(MeetingCanceledEvent.class, event -> {
                    assertThat(event.invitees()).hasSize(2);
                    assertThat(event.invitees().getFirst().email()).isEqualTo("alice@example.com");
                    assertThat(event.invitees().get(1).email()).isEqualTo("bob@example.com");
                    assertThat(event.cancelReason()).isEqualTo(CancelReason.HOST_CANCELED.name());
                });
    }

    @Test
    void cancelOnRunningMeetingReturnsInvalidStatusTransitionWithNoEvent() {
        Meeting meeting = reconstituted(MeetingStatus.RUNNING);

        Result<Void, MeetingError> result = meeting.cancel(CancelReason.HOST_CANCELED);

        assertThat(result.isFailure()).isTrue();
        assertThat(((Result.Failure<Void, MeetingError>) result).error())
                .isInstanceOf(MeetingError.InvalidStatusTransition.class);
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.RUNNING);
        assertThat(meeting.getDomainEvents()).isEmpty();
    }

    @Test
    void cancelOnCompletedMeetingReturnsInvalidStatusTransitionWithNoEvent() {
        Meeting meeting = reconstituted(MeetingStatus.COMPLETED);

        Result<Void, MeetingError> result = meeting.cancel(CancelReason.HOST_CANCELED);

        assertThat(result.isFailure()).isTrue();
        assertThat(((Result.Failure<Void, MeetingError>) result).error())
                .isInstanceOf(MeetingError.InvalidStatusTransition.class);
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.COMPLETED);
        assertThat(meeting.getDomainEvents()).isEmpty();
    }

    @Test
    void cancelOnAlreadyCanceledMeetingReturnsInvalidStatusTransitionWithNoEvent() {
        Meeting meeting = reconstituted(MeetingStatus.CANCELED);

        Result<Void, MeetingError> result = meeting.cancel(CancelReason.HOST_CANCELED);

        assertThat(result.isFailure()).isTrue();
        assertThat(((Result.Failure<Void, MeetingError>) result).error())
                .isInstanceOf(MeetingError.InvalidStatusTransition.class);
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.CANCELED);
        assertThat(meeting.getDomainEvents()).isEmpty();
    }

    @Test
    void cancelWithAllFieldsPopulatesEventCorrectly() {
        Meeting meeting = scheduledMeeting();
        Instant startTime = Instant.now().plus(1, ChronoUnit.HOURS);

        meeting.cancel(CancelReason.NO_SHOW, "Title", "abc-short-code", startTime, List.of());

        assertThat(meeting.getDomainEvents())
                .singleElement()
                .isInstanceOfSatisfying(MeetingCanceledEvent.class, event -> {
                    assertThat(event.tenantId()).isEqualTo("tenant");
                    assertThat(event.meetingId()).isEqualTo(meeting.getId().value());
                    assertThat(event.hostId()).isEqualTo("host");
                    assertThat(event.cancelReason()).isEqualTo(CancelReason.NO_SHOW.name());
                    assertThat(event.meetingTitle()).isEqualTo("Title");
                    assertThat(event.meetingShortCode()).isEqualTo("abc-short-code");
                    assertThat(event.startTime()).isEqualTo(startTime);
                    assertThat(event.canceledAt()).isNotNull();
                    assertThat(event.invitees()).isEmpty();
                });
    }

    private Meeting scheduledMeeting() {
        return reconstituted(MeetingStatus.SCHEDULED);
    }

    private Meeting reconstituted(MeetingStatus status) {
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
        Meeting meeting = Meeting.reconstitute(
                TenantId.of("tenant"),
                MeetingId.of(UUID.randomUUID()),
                AccountId.of("host"),
                ShortCode.of("ABC123DEF0"),
                MeetingTitle.of("Title"),
                "Description",
                JiraIssueLink.of("ISS-1", "PROJ-1", "PROJ"),
                MeetingTimeRange.of(start, start.plus(1, ChronoUnit.HOURS)),
                null,
                MeetingType.SCHEDULED,
                status,
                MeetingSettings.defaults(),
                MeetingTimeZone.of("Asia/Ho_Chi_Minh"),
                Email.of("host@example.com"),
                InviteeDisplayName.of("Host User"),
                "calendar@example.com",
                0,
                start.minus(1, ChronoUnit.DAYS),
                start.minus(1, ChronoUnit.DAYS),
                null,
                null,
                null,
                null);
        meeting.clearDomainEvents();
        return meeting;
    }
}
