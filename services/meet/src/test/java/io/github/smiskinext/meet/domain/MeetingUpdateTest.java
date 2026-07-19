package io.github.smiskinext.meet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meet.domain.event.MeetingInfoUpdatedEvent;
import io.github.smiskinext.meet.domain.event.MeetingSettingsUpdatedEvent;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.valueobject.*;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class MeetingUpdateTest {

    @Test
    void scheduledHostUpdatePublishesOneEventPerChangedGroup() {
        Meeting meeting = scheduledMeeting();
        meeting.clearDomainEvents();
        MeetingSettings settings = new MeetingSettings(
                io.github.smiskinext.meet.domain.model.AdmissionPolicy.ALLOW_ALL,
                50,
                true,
                true,
                true,
                true);

        Result<Void, MeetingError> result = meeting.update(
                AccountId.of("host"),
                MeetingTitle.of("Updated"),
                "Updated description",
                JiraIssueLink.of("ISS-2", "PROJ-2", "PROJ"),
                settings,
                MeetingTimeZone.of("UTC"),
                MeetingTimeRange.of(
                        Instant.now().plus(3, ChronoUnit.HOURS),
                        Instant.now().plus(4, ChronoUnit.HOURS)));

        assertThat(result.isSuccess()).isTrue();
        assertThat(meeting.getDomainEvents()).hasSize(2);
        assertThat(meeting.getDomainEvents()).anyMatch(MeetingInfoUpdatedEvent.class::isInstance);
        assertThat(meeting.getDomainEvents())
                .anyMatch(MeetingSettingsUpdatedEvent.class::isInstance);
    }

    @Test
    void noOpUpdatePublishesNoEvent() {
        Meeting meeting = scheduledMeeting();
        meeting.clearDomainEvents();

        Result<Void, MeetingError> result = meeting.update(
                AccountId.of("host"),
                meeting.getTitle(),
                meeting.getDescription(),
                meeting.getIssueLink(),
                meeting.getSettings(),
                meeting.getTimeZone(),
                meeting.getTimeRange().orElseThrow());

        assertThat(result.isSuccess()).isTrue();
        assertThat(meeting.getDomainEvents()).isEmpty();
    }

    @Test
    void nonHostAndRunningScheduledFieldChangesAreRejected() {
        Meeting meeting = scheduledMeeting();
        meeting.clearDomainEvents();

        Result<Void, MeetingError> nonHost = meeting.update(
                AccountId.of("other"),
                meeting.getTitle(),
                meeting.getDescription(),
                meeting.getIssueLink(),
                meeting.getSettings(),
                meeting.getTimeZone(),
                meeting.getTimeRange().orElseThrow());
        assertThat(nonHost).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<Void, MeetingError>) nonHost).error())
                .isInstanceOf(MeetingError.NotAuthorized.class);

        assertThat(meeting.start().isSuccess()).isTrue();
        Result<Void, MeetingError> running = meeting.update(
                AccountId.of("host"),
                meeting.getTitle(),
                meeting.getDescription(),
                meeting.getIssueLink(),
                meeting.getSettings(),
                MeetingTimeZone.of("UTC"),
                meeting.getTimeRange().orElseThrow());
        assertThat(((Result.Failure<Void, MeetingError>) running).error())
                .isInstanceOf(MeetingError.InvalidStatusTransition.class);
    }

    @Test
    void runningMeetingAcceptsInformationAndSettingsWithPastScheduledRange() {
        Meeting meeting = reconstituted(MeetingStatus.RUNNING);
        MeetingSettings settings = new MeetingSettings(
                io.github.smiskinext.meet.domain.model.AdmissionPolicy.ALLOW_ALL,
                25,
                true,
                true,
                true,
                true);

        Result<Void, MeetingError> result = meeting.update(
                AccountId.of("host"),
                MeetingTitle.of("Running update"),
                "Changed while running",
                meeting.getIssueLink(),
                settings,
                meeting.getTimeZone(),
                meeting.getTimeRange().orElseThrow());

        assertThat(result.isSuccess()).isTrue();
        assertThat(meeting.getTitle().value()).isEqualTo("Running update");
        assertThat(meeting.getDomainEvents()).hasSize(2);
    }

    @Test
    void terminalMeetingsRejectAllUpdatesWithoutEvents() {
        for (MeetingStatus status :
                new MeetingStatus[] {MeetingStatus.COMPLETED, MeetingStatus.CANCELED}) {
            Meeting meeting = reconstituted(status);

            Result<Void, MeetingError> result = meeting.update(
                    AccountId.of("host"),
                    MeetingTitle.of("Rejected"),
                    meeting.getDescription(),
                    meeting.getIssueLink(),
                    meeting.getSettings(),
                    meeting.getTimeZone(),
                    meeting.getTimeRange().orElseThrow());

            assertThat(result.isFailure()).isTrue();
            assertThat(meeting.getTitle().value()).isEqualTo("Title");
            assertThat(meeting.getDomainEvents()).isEmpty();
        }
    }

    private Meeting scheduledMeeting() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Result<Meeting, MeetingError> result = Meeting.schedule(
                TenantId.of("tenant"),
                AccountId.of("host"),
                MeetingTitle.of("Title"),
                "Description",
                JiraIssueLink.of("ISS-1", "PROJ-1", "PROJ"),
                MeetingTimeRange.of(start, start.plus(1, ChronoUnit.HOURS)),
                MeetingSettings.defaults(),
                MeetingTimeZone.of("Asia/Ho_Chi_Minh"),
                ShortCode.of("ABC123DEF0"));
        return ((Result.Success<Meeting, MeetingError>) result).value();
    }

    private Meeting reconstituted(MeetingStatus status) {
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
        return Meeting.reconstitute(
                TenantId.of("tenant"),
                MeetingId.of(java.util.UUID.randomUUID()),
                AccountId.of("host"),
                ShortCode.of("ABC123DEF0"),
                MeetingTitle.of("Title"),
                "Description",
                JiraIssueLink.of("ISS-1", "PROJ-1", "PROJ"),
                MeetingTimeRange.of(start, start.plus(1, ChronoUnit.HOURS)),
                null,
                io.github.smiskinext.meet.domain.model.MeetingType.SCHEDULED,
                status,
                MeetingSettings.defaults(),
                MeetingTimeZone.of("Asia/Ho_Chi_Minh"),
                start.minus(1, ChronoUnit.DAYS),
                null,
                null,
                null,
                null);
    }
}
