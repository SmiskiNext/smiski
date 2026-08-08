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
import java.util.List;
import org.junit.jupiter.api.Test;

class MeetingUpdateTest {

    @Test
    void scheduledHostUpdateInfoPublishesOnlyInfoEvent() {
        Meeting meeting = scheduledMeeting();
        String calendarUid = meeting.getCalendarUid();
        meeting.clearDomainEvents();

        Result<Void, MeetingError> result = meeting.updateInfo(
                AccountId.of("host"),
                MeetingTitle.of("Updated"),
                "Updated description",
                JiraIssueLink.of("ISS-2", "PROJ-2", "PROJ"),
                MeetingTimeZone.of("UTC"),
                MeetingTimeRange.of(
                        Instant.now().plus(3, ChronoUnit.HOURS),
                        Instant.now().plus(4, ChronoUnit.HOURS)),
                List.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(meeting.getDomainEvents())
                .singleElement()
                .isInstanceOf(MeetingInfoUpdatedEvent.class);
        assertThat(meeting.getCalendarUid()).isEqualTo(calendarUid);
        assertThat(meeting.getCalendarSequence()).isEqualTo(1);
    }

    @Test
    void noOpUpdateInfoPublishesNoEvent() {
        Meeting meeting = scheduledMeeting();
        int initialSequence = meeting.getCalendarSequence();
        meeting.clearDomainEvents();

        Result<Void, MeetingError> result = meeting.updateInfo(
                AccountId.of("host"),
                meeting.getTitle(),
                meeting.getDescription(),
                meeting.getIssueLink(),
                meeting.getTimeZone(),
                meeting.getTimeRange().orElseThrow(),
                List.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(meeting.getDomainEvents()).isEmpty();
        assertThat(meeting.getCalendarSequence()).isEqualTo(initialSequence);
    }

    @Test
    void nonHostAndRunningScheduledFieldChangesAreRejected() {
        Meeting meeting = scheduledMeeting();
        meeting.clearDomainEvents();

        Result<Void, MeetingError> nonHost = meeting.updateInfo(
                AccountId.of("other"),
                meeting.getTitle(),
                meeting.getDescription(),
                meeting.getIssueLink(),
                meeting.getTimeZone(),
                meeting.getTimeRange().orElseThrow(),
                List.of());
        assertThat(nonHost).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<Void, MeetingError>) nonHost).error())
                .isInstanceOf(MeetingError.NotAuthorized.class);

        assertThat(meeting.start().isSuccess()).isTrue();
        Result<Void, MeetingError> running = meeting.updateInfo(
                AccountId.of("host"),
                meeting.getTitle(),
                meeting.getDescription(),
                meeting.getIssueLink(),
                MeetingTimeZone.of("UTC"),
                meeting.getTimeRange().orElseThrow(),
                List.of());
        assertThat(((Result.Failure<Void, MeetingError>) running).error())
                .isInstanceOf(MeetingError.InvalidStatusTransition.class);
    }

    @Test
    void runningMeetingAcceptsInformationWithoutScheduledFieldChange() {
        Meeting meeting = reconstituted(MeetingStatus.RUNNING);

        Result<Void, MeetingError> result = meeting.updateInfo(
                AccountId.of("host"),
                MeetingTitle.of("Running update"),
                "Changed while running",
                meeting.getIssueLink(),
                meeting.getTimeZone(),
                meeting.getTimeRange().orElseThrow(),
                List.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(meeting.getTitle().value()).isEqualTo("Running update");
        assertThat(meeting.getDomainEvents())
                .singleElement()
                .isInstanceOf(MeetingInfoUpdatedEvent.class);
    }

    @Test
    void terminalMeetingsAcceptInformationFields() {
        for (MeetingStatus status :
                new MeetingStatus[] {MeetingStatus.COMPLETED, MeetingStatus.CANCELED}) {
            Meeting meeting = reconstituted(status);

            Result<Void, MeetingError> result = meeting.updateInfo(
                    AccountId.of("host"),
                    MeetingTitle.of("Corrected"),
                    "Corrected description",
                    JiraIssueLink.of("ISS-9", "PROJ-9", "PROJ"),
                    meeting.getTimeZone(),
                    meeting.getTimeRange().orElseThrow(),
                    List.of());

            assertThat(result.isSuccess()).isTrue();
            assertThat(meeting.getStatus()).isEqualTo(status);
            assertThat(meeting.getTitle().value()).isEqualTo("Corrected");
            assertThat(meeting.getDescription()).isEqualTo("Corrected description");
            assertThat(meeting.getIssueLink())
                    .isEqualTo(JiraIssueLink.of("ISS-9", "PROJ-9", "PROJ"));
            assertThat(meeting.getDomainEvents())
                    .singleElement()
                    .isInstanceOf(MeetingInfoUpdatedEvent.class);
        }
    }

    @Test
    void terminalMeetingsRejectScheduledFieldChangesWithoutEvents() {
        for (MeetingStatus status :
                new MeetingStatus[] {MeetingStatus.COMPLETED, MeetingStatus.CANCELED}) {
            Meeting meeting = reconstituted(status);

            Result<Void, MeetingError> result = meeting.updateInfo(
                    AccountId.of("host"),
                    MeetingTitle.of("Rejected"),
                    meeting.getDescription(),
                    meeting.getIssueLink(),
                    MeetingTimeZone.of("UTC"),
                    meeting.getTimeRange().orElseThrow(),
                    List.of());

            assertThat(result.isFailure()).isTrue();
            assertThat(((Result.Failure<Void, MeetingError>) result).error())
                    .isInstanceOf(MeetingError.InvalidStatusTransition.class);
            assertThat(meeting.getTitle().value()).isEqualTo("Title");
            assertThat(meeting.getTimeZone()).isEqualTo(MeetingTimeZone.of("Asia/Ho_Chi_Minh"));
            assertThat(meeting.getDomainEvents()).isEmpty();
        }
    }

    @Test
    void terminalMeetingsRejectTimeRangeChangesWithoutEvents() {
        Instant newStart = Instant.now().plus(5, ChronoUnit.HOURS);
        for (MeetingStatus status :
                new MeetingStatus[] {MeetingStatus.COMPLETED, MeetingStatus.CANCELED}) {
            Meeting meeting = reconstituted(status);
            MeetingTimeRange originalRange = meeting.getTimeRange().orElseThrow();

            Result<Void, MeetingError> result = meeting.updateInfo(
                    AccountId.of("host"),
                    meeting.getTitle(),
                    meeting.getDescription(),
                    meeting.getIssueLink(),
                    meeting.getTimeZone(),
                    MeetingTimeRange.of(newStart, newStart.plus(1, ChronoUnit.HOURS)),
                    List.of());

            assertThat(result.isFailure()).isTrue();
            assertThat(((Result.Failure<Void, MeetingError>) result).error())
                    .isInstanceOf(MeetingError.InvalidStatusTransition.class);
            assertThat(meeting.getTimeRange()).contains(originalRange);
            assertThat(meeting.getDomainEvents()).isEmpty();
        }
    }

    @Test
    void runningMeetingRejectsTimeRangeChangeWithoutEvents() {
        Meeting meeting = reconstituted(MeetingStatus.RUNNING);
        MeetingTimeRange originalRange = meeting.getTimeRange().orElseThrow();
        Instant newStart = Instant.now().plus(5, ChronoUnit.HOURS);

        Result<Void, MeetingError> result = meeting.updateInfo(
                AccountId.of("host"),
                meeting.getTitle(),
                meeting.getDescription(),
                meeting.getIssueLink(),
                meeting.getTimeZone(),
                MeetingTimeRange.of(newStart, newStart.plus(1, ChronoUnit.HOURS)),
                List.of());

        assertThat(result.isFailure()).isTrue();
        assertThat(((Result.Failure<Void, MeetingError>) result).error())
                .isInstanceOf(MeetingError.InvalidStatusTransition.class);
        assertThat(meeting.getTimeRange()).contains(originalRange);
        assertThat(meeting.getDomainEvents()).isEmpty();
    }

    @Test
    void terminalMeetingIssueLinkOnlyChangeIsAccepted() {
        Meeting meeting = reconstituted(MeetingStatus.COMPLETED);

        Result<Void, MeetingError> result = meeting.updateInfo(
                AccountId.of("host"),
                meeting.getTitle(),
                meeting.getDescription(),
                JiraIssueLink.of("ISS-42", "PROJ-42", "PROJ"),
                meeting.getTimeZone(),
                meeting.getTimeRange().orElseThrow(),
                List.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(meeting.getIssueLink()).isEqualTo(JiraIssueLink.of("ISS-42", "PROJ-42", "PROJ"));
        assertThat(meeting.getDomainEvents())
                .singleElement()
                .isInstanceOf(MeetingInfoUpdatedEvent.class);
    }

    @Test
    void hostUpdateSettingsPublishesOnlySettingsEvent() {
        Meeting meeting = scheduledMeeting();
        String calendarUid = meeting.getCalendarUid();
        int initialSequence = meeting.getCalendarSequence();
        meeting.clearDomainEvents();
        MeetingSettings settings = new MeetingSettings(
                io.github.smiskinext.meet.domain.model.AdmissionPolicy.ALLOW_ALL,
                25,
                true,
                true,
                true,
                true);

        Result<Void, MeetingError> result = meeting.updateSettings(AccountId.of("host"), settings);

        assertThat(result.isSuccess()).isTrue();
        assertThat(meeting.getDomainEvents())
                .singleElement()
                .isInstanceOf(MeetingSettingsUpdatedEvent.class);
        assertThat(meeting.getSettings()).isEqualTo(settings);
        assertThat(meeting.getCalendarUid()).isEqualTo(calendarUid);
        assertThat(meeting.getCalendarSequence()).isEqualTo(initialSequence);
    }

    @Test
    void noOpUpdateSettingsPublishesNoEvent() {
        Meeting meeting = scheduledMeeting();
        meeting.clearDomainEvents();

        Result<Void, MeetingError> result =
                meeting.updateSettings(AccountId.of("host"), meeting.getSettings());

        assertThat(result.isSuccess()).isTrue();
        assertThat(meeting.getDomainEvents()).isEmpty();
    }

    @Test
    void nonHostUpdateSettingsIsRejected() {
        Meeting meeting = scheduledMeeting();
        meeting.clearDomainEvents();
        MeetingSettings settings = new MeetingSettings(
                io.github.smiskinext.meet.domain.model.AdmissionPolicy.ALLOW_ALL,
                25,
                true,
                true,
                true,
                true);

        Result<Void, MeetingError> result = meeting.updateSettings(AccountId.of("other"), settings);

        assertThat(result.isFailure()).isTrue();
        assertThat(((Result.Failure<Void, MeetingError>) result).error())
                .isInstanceOf(MeetingError.NotAuthorized.class);
        assertThat(meeting.getDomainEvents()).isEmpty();
    }

    @Test
    void terminalMeetingsRejectSettingsUpdatesWithoutEvents() {
        MeetingSettings settings = new MeetingSettings(
                io.github.smiskinext.meet.domain.model.AdmissionPolicy.ALLOW_ALL,
                25,
                true,
                true,
                true,
                true);
        for (MeetingStatus status :
                new MeetingStatus[] {MeetingStatus.COMPLETED, MeetingStatus.CANCELED}) {
            Meeting meeting = reconstituted(status);

            Result<Void, MeetingError> result =
                    meeting.updateSettings(AccountId.of("host"), settings);

            assertThat(result.isFailure()).isTrue();
            assertThat(meeting.getSettings()).isEqualTo(MeetingSettings.defaults());
            assertThat(meeting.getDomainEvents()).isEmpty();
        }
    }

    @Test
    void runningMeetingAcceptsSettingsReplacement() {
        Meeting meeting = reconstituted(MeetingStatus.RUNNING);
        MeetingSettings settings = new MeetingSettings(
                io.github.smiskinext.meet.domain.model.AdmissionPolicy.ALLOW_ALL,
                25,
                false,
                true,
                true,
                true);

        Result<Void, MeetingError> result = meeting.updateSettings(AccountId.of("host"), settings);

        assertThat(result.isSuccess()).isTrue();
        assertThat(meeting.getSettings()).isEqualTo(settings);
        assertThat(meeting.getDomainEvents())
                .singleElement()
                .isInstanceOf(MeetingSettingsUpdatedEvent.class);
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
                Email.of("host@example.com"),
                InviteeDisplayName.of("Host User"),
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
