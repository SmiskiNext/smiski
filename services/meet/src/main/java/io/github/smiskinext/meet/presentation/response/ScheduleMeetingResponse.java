package io.github.smiskinext.meet.presentation.response;

import io.github.smiskinext.meet.application.result.ScheduleMeetingResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Response for a successfully created scheduled meeting")
public record ScheduleMeetingResponse(Meeting meeting) {

    @Schema(name = "ScheduledMeetingSnapshot", description = "Scheduled meeting snapshot")
    public record Meeting(
            UUID id,
            String hostId,
            String shortCode,
            String type,
            String status,
            String title,
            String description,
            IssueLink issueLink,
            Settings settings,
            Instant startTime,
            Instant endTime,
            String zoneId,
            String organizerEmail,
            String organizerDisplayName,
            String calendarUid,
            int calendarSequence,
            Instant createdAt) {}

    public record IssueLink(String issueId, String issueKey, String projectKey) {}

    public record Settings(
            String admissionPolicy,
            int maxParticipants,
            boolean allowScreenShare,
            boolean chatEnabled,
            boolean allowMicrophone,
            boolean allowVideo) {}

    public static ScheduleMeetingResponse from(ScheduleMeetingResult result) {
        IssueLink issueLink = new IssueLink(
                result.issueLink().issueId(),
                result.issueLink().issueKey(),
                result.issueLink().projectKey());

        Settings settings = new Settings(
                result.settings().admissionPolicy(),
                result.settings().maxParticipants(),
                result.settings().allowScreenShare(),
                result.settings().chatEnabled(),
                result.settings().allowMicrophone(),
                result.settings().allowVideo());

        Meeting meeting = new Meeting(
                result.meetingId(),
                result.hostId(),
                result.shortCode(),
                result.type(),
                result.status(),
                result.title(),
                result.description(),
                issueLink,
                settings,
                result.startTime(),
                result.endTime(),
                result.zoneId(),
                result.organizerEmail(),
                result.organizerDisplayName(),
                result.calendarUid(),
                result.calendarSequence(),
                result.createdAt());

        return new ScheduleMeetingResponse(meeting);
    }
}
