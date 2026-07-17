package io.github.smiskinext.meet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meet.domain.event.MeetingCreatedEvent;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.valueobject.*;
import io.github.smiskinext.shared.domain.DomainEvent;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class MeetingScheduleTest {

    private static final TenantId TENANT = TenantId.of("tenant-1");
    private static final AccountId HOST = AccountId.of("host-1");
    private static final MeetingTitle TITLE = MeetingTitle.of("Scheduled Meeting");
    private static final String DESCRIPTION = "A scheduled meeting description";
    private static final JiraIssueLink ISSUE_LINK = JiraIssueLink.of("ISS-1", "PROJ-1", "PROJ");
    private static final MeetingSettings SETTINGS = MeetingSettings.defaults();
    private static final ShortCode SHORT_CODE = ShortCode.of("ABC123DEF0");

    @Test
    void futureStartTimeIsAccepted() {
        Instant now = Instant.now();
        Instant start = now.plus(1, ChronoUnit.HOURS);
        Instant end = now.plus(2, ChronoUnit.HOURS);
        MeetingTimeRange timeRange = MeetingTimeRange.of(start, end);

        Result<Meeting, MeetingError> result = Meeting.schedule(
                TENANT, HOST, TITLE, DESCRIPTION, ISSUE_LINK, timeRange, SETTINGS, SHORT_CODE);

        assertThat(result.isSuccess()).isTrue();
        Meeting meeting = ((Result.Success<Meeting, MeetingError>) result).value();
        assertThat(meeting.getType().name()).isEqualTo("SCHEDULED");
        assertThat(meeting.getStatus().name()).isEqualTo("SCHEDULED");
        assertThat(meeting.getStartTime()).hasValue(start);
        assertThat(meeting.getEndTime()).hasValue(end);
    }

    @Test
    void startTimeWithinClockSkewToleranceIsAccepted() {
        Instant now = Instant.now();
        Instant start = now.minus(1, ChronoUnit.MINUTES);
        Instant end = now.plus(1, ChronoUnit.HOURS);
        MeetingTimeRange timeRange = MeetingTimeRange.of(start, end);

        Result<Meeting, MeetingError> result = Meeting.schedule(
                TENANT, HOST, TITLE, DESCRIPTION, ISSUE_LINK, timeRange, SETTINGS, SHORT_CODE);

        assertThat(result.isSuccess()).isTrue();
        Meeting meeting = ((Result.Success<Meeting, MeetingError>) result).value();
        assertThat(meeting.getType().name()).isEqualTo("SCHEDULED");
    }

    @Test
    void pastStartTimeReturnsStartTimeInPast() {
        Instant now = Instant.now();
        Instant start = now.minus(1, ChronoUnit.HOURS);
        Instant end = now.plus(1, ChronoUnit.HOURS);
        MeetingTimeRange timeRange = MeetingTimeRange.of(start, end);

        Result<Meeting, MeetingError> result = Meeting.schedule(
                TENANT, HOST, TITLE, DESCRIPTION, ISSUE_LINK, timeRange, SETTINGS, SHORT_CODE);

        assertThat(result.isFailure()).isTrue();
        MeetingError error = ((Result.Failure<Meeting, MeetingError>) result).error();
        assertThat(error).isInstanceOf(MeetingError.StartTimeInPast.class);
        assertThat(((MeetingError.StartTimeInPast) error).startTime()).isEqualTo(start);
    }

    @Test
    void createdEventCarriesBothStartAndEndTime() {
        Instant now = Instant.now();
        Instant start = now.plus(1, ChronoUnit.HOURS);
        Instant end = now.plus(2, ChronoUnit.HOURS);
        MeetingTimeRange timeRange = MeetingTimeRange.of(start, end);

        Result<Meeting, MeetingError> result = Meeting.schedule(
                TENANT, HOST, TITLE, DESCRIPTION, ISSUE_LINK, timeRange, SETTINGS, SHORT_CODE);

        assertThat(result.isSuccess()).isTrue();
        Meeting meeting = ((Result.Success<Meeting, MeetingError>) result).value();

        assertThat(meeting.getDomainEvents()).hasSize(1);
        DomainEvent event = meeting.getDomainEvents().getFirst();
        assertThat(event).isInstanceOf(MeetingCreatedEvent.class);

        MeetingCreatedEvent createdEvent = (MeetingCreatedEvent) event;
        assertThat(createdEvent.startTime()).isEqualTo(start);
        assertThat(createdEvent.endTime()).isEqualTo(end);
    }
}
