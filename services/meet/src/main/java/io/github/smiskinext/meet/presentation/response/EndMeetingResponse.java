package io.github.smiskinext.meet.presentation.response;

import io.github.smiskinext.meet.application.result.EndMeetingResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(description = "Snapshot of a meeting ended by its host")
public record EndMeetingResponse(Meeting meeting) {

    @Schema(name = "EndedMeetingSnapshot", description = "Ended meeting snapshot")
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
            @Nullable Instant startTime,
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

    public static EndMeetingResponse from(EndMeetingResult result) {
        return new EndMeetingResponse(toMeeting(result));
    }

    static Meeting toMeeting(EndMeetingResult result) {
        return new Meeting(
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
                result.organizerEmail(),
                result.organizerDisplayName(),
                result.calendarUid(),
                result.calendarSequence(),
                result.createdAt());
    }
}
