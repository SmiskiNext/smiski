package io.github.smiskinext.meet.presentation.response;

import io.github.smiskinext.meet.application.result.CreateInstantMeetingResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Response for a successfully created instant meeting")
public record CreateInstantMeetingResponse(Meeting meeting, LiveKit livekit) {

    @Schema(description = "Meeting snapshot (excludes tenantId)")
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
            Instant createdAt) {}

    public record IssueLink(String issueId, String issueKey, String projectKey) {}

    public record Settings(
            String admissionPolicy,
            int maxParticipants,
            boolean allowScreenShare,
            boolean chatEnabled,
            boolean allowMicrophone,
            boolean allowVideo) {}

    @Schema(description = "LiveKit access details for the host")
    public record LiveKit(String token, String roomName) {}

    public static CreateInstantMeetingResponse from(CreateInstantMeetingResult result) {
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
                result.createdAt());

        LiveKit livekit = new LiveKit(result.livekit().token(), result.livekit().roomName());

        return new CreateInstantMeetingResponse(meeting, livekit);
    }
}
