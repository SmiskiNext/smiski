package io.github.smiskinext.meet.presentation.response;

import io.github.smiskinext.meet.application.result.UpdateMeetingResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Updated meeting snapshot")
public record UpdateMeetingResponse(Meeting meeting) {
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
            Instant createdAt) {}

    public record IssueLink(String issueId, String issueKey, String projectKey) {}

    public record Settings(
            String admissionPolicy,
            int maxParticipants,
            boolean allowScreenShare,
            boolean chatEnabled,
            boolean allowMicrophone,
            boolean allowVideo) {}

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
                new Settings(
                        result.settings().admissionPolicy(),
                        result.settings().maxParticipants(),
                        result.settings().allowScreenShare(),
                        result.settings().chatEnabled(),
                        result.settings().allowMicrophone(),
                        result.settings().allowVideo()),
                result.startTime(),
                result.endTime(),
                result.zoneId(),
                result.createdAt()));
    }
}
