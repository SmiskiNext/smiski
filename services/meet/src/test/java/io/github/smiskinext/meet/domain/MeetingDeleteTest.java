package io.github.smiskinext.meet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meet.domain.event.MeetingDeletedEvent;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.MeetingType;
import io.github.smiskinext.meet.domain.model.valueobject.*;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeetingDeleteTest {

    @Test
    void hostDeletesEligibleMeetingRecordsStateAndRegistersEvent() {
        for (MeetingStatus status : new MeetingStatus[] {
            MeetingStatus.SCHEDULED, MeetingStatus.COMPLETED, MeetingStatus.CANCELED
        }) {
            Meeting meeting = reconstituted(status);

            Result<Void, MeetingError> result = meeting.delete(AccountId.of("host"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(meeting.getDeletedAt()).isPresent();
            assertThat(meeting.getDeletedBy()).contains(AccountId.of("host"));
            assertThat(meeting.getPurgeAfter()).isEmpty();
            assertThat(meeting.getDomainEvents())
                    .singleElement()
                    .isInstanceOfSatisfying(MeetingDeletedEvent.class, event -> {
                        assertThat(event.tenantId()).isEqualTo("tenant");
                        assertThat(event.meetingId()).isEqualTo(meeting.getId().value());
                        assertThat(event.hostId()).isEqualTo("host");
                        assertThat(event.shortCode()).isEqualTo("abc123def0");
                        assertThat(event.type()).isEqualTo(MeetingType.SCHEDULED.name());
                        assertThat(event.status()).isEqualTo(status.name());
                        assertThat(event.title()).isEqualTo("Title");
                        assertThat(event.description()).isEqualTo("Description");
                        assertThat(event.issueId()).isEqualTo("ISS-1");
                        assertThat(event.issueKey()).isEqualTo("PROJ-1");
                        assertThat(event.projectKey()).isEqualTo("PROJ");
                        assertThat(event.startTime()).isNotNull();
                        assertThat(event.endTime()).isNotNull();
                        assertThat(event.settings()).isEqualTo(MeetingSettings.defaults());
                        assertThat(event.zoneId()).isEqualTo("Asia/Ho_Chi_Minh");
                        assertThat(event.organizerEmail()).isEqualTo("host@example.com");
                        assertThat(event.organizerDisplayName()).isEqualTo("Host User");
                        assertThat(event.calendarUid()).isEqualTo("calendar@example.com");
                        assertThat(event.createdAt()).isNotNull();
                        assertThat(event.deletedBy()).isEqualTo("host");
                        assertThat(event.deletedAt()).isNotNull();
                    });
        }
    }

    @Test
    void nonHostDeleteIsRejectedWithoutEvent() {
        Meeting meeting = reconstituted(MeetingStatus.SCHEDULED);

        Result<Void, MeetingError> result = meeting.delete(AccountId.of("other"));

        assertThat(result.isFailure()).isTrue();
        assertThat(((Result.Failure<Void, MeetingError>) result).error())
                .isInstanceOf(MeetingError.NotAuthorized.class);
        assertThat(meeting.getDeletedAt()).isEmpty();
        assertThat(meeting.getDomainEvents()).isEmpty();
    }

    @Test
    void runningMeetingCannotBeDeleted() {
        Meeting meeting = reconstituted(MeetingStatus.RUNNING);

        Result<Void, MeetingError> result = meeting.delete(AccountId.of("host"));

        assertThat(result.isFailure()).isTrue();
        assertThat(((Result.Failure<Void, MeetingError>) result).error())
                .isInstanceOf(MeetingError.CannotDeleteRunningMeeting.class);
        assertThat(meeting.getDeletedAt()).isEmpty();
        assertThat(meeting.getDomainEvents()).isEmpty();
    }

    private Meeting reconstituted(MeetingStatus status) {
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
        return Meeting.reconstitute(
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
    }
}
