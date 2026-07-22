package io.github.smiskinext.meet.presentation.response;

import io.github.smiskinext.meet.application.result.GetMeetingResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(description = "A single meeting with its invitees and joined participants")
public record GetMeetingResponse(
        Meeting meeting, List<Invitee> invitees, List<Participant> participants) {

    @Schema(name = "MeetingDetailSnapshot", description = "Meeting detail snapshot")
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

            @Schema(
                    description = "Scheduled start time; null for instant meetings",
                    nullable = true)
            @Nullable Instant startTime,

            @Schema(description = "End time; null until scheduled or ended", nullable = true)
            @Nullable Instant endTime,

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

    @Schema(name = "MeetingDetailInvitee", description = "Active invitee with RSVP status")
    public record Invitee(
            String accountId,
            String email,
            String displayName,
            String status,
            Instant invitedAt,

            @Schema(
                    description = "Timestamp when the invitee responded; null if not yet responded",
                    nullable = true)
            @Nullable Instant respondedAt) {}

    @Schema(name = "MeetingDetailParticipant", description = "Distinct joined participant")
    public record Participant(
            String accountId,

            @Schema(description = "Cached display name; null when unknown", nullable = true)
            @Nullable String displayName,

            String role,
            Instant joinedAt,

            @Schema(
                    description = "Latest leave time; null while the participant is still present",
                    nullable = true)
            @Nullable Instant leftAt) {}

    public static GetMeetingResponse from(GetMeetingResult result) {
        GetMeetingResult.Meeting meeting = result.meeting();
        Meeting meetingView = new Meeting(
                meeting.id(),
                meeting.hostId(),
                meeting.shortCode(),
                meeting.type(),
                meeting.status(),
                meeting.title(),
                meeting.description(),
                new IssueLink(
                        meeting.issueLink().issueId(),
                        meeting.issueLink().issueKey(),
                        meeting.issueLink().projectKey()),
                new Settings(
                        meeting.settings().admissionPolicy(),
                        meeting.settings().maxParticipants(),
                        meeting.settings().allowScreenShare(),
                        meeting.settings().chatEnabled(),
                        meeting.settings().allowMicrophone(),
                        meeting.settings().allowVideo()),
                meeting.startTime(),
                meeting.endTime(),
                meeting.zoneId(),
                meeting.organizerEmail(),
                meeting.organizerDisplayName(),
                meeting.calendarUid(),
                meeting.calendarSequence(),
                meeting.createdAt());

        List<Invitee> invitees = result.invitees().stream()
                .map(invitee -> new Invitee(
                        invitee.accountId(),
                        invitee.email(),
                        invitee.displayName(),
                        invitee.status(),
                        invitee.invitedAt(),
                        invitee.respondedAt()))
                .toList();

        List<Participant> participants = result.participants().stream()
                .map(participant -> new Participant(
                        participant.accountId(),
                        participant.displayName(),
                        participant.role(),
                        participant.joinedAt(),
                        participant.leftAt()))
                .toList();

        return new GetMeetingResponse(meetingView, invitees, participants);
    }
}
