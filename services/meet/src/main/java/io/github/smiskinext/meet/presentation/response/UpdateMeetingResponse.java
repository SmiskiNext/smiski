package io.github.smiskinext.meet.presentation.response;

import io.github.smiskinext.meet.application.result.UpdateMeetingResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Updated meeting snapshot")
public record UpdateMeetingResponse(Meeting meeting) {
    @Schema(name = "UpdatedMeetingSnapshot", description = "Updated meeting snapshot")
    public record Meeting(
            UUID id,
            String hostId,
            String shortCode,
            String type,
            String status,
            String title,
            String description,
            IssueLink issueLink,
            Instant startTime,
            Instant endTime,
            String zoneId,
            String organizerEmail,
            String organizerDisplayName,
            String calendarUid,
            int calendarSequence,
            Instant createdAt) {}

    public record IssueLink(String issueId, String issueKey, String projectKey) {}

    public static UpdateMeetingResponse from(UpdateMeetingResult result) {
        return new UpdateMeetingResponse(new Meeting(
                result.meetingId(),
                result.hostId(),
                result.shortCode(),
                result.type(),
                result.status(),
                result.title(),
                result.description(),
                new IssueLink(
                        result.issueLink().issueId(),
                        result.issueLink().issueKey(),
                        result.issueLink().projectKey()),
                result.startTime(),
                result.endTime(),
                result.zoneId(),
                result.organizerEmail(),
                result.organizerDisplayName(),
                result.calendarUid(),
                result.calendarSequence(),
                result.createdAt()));
    }
}
