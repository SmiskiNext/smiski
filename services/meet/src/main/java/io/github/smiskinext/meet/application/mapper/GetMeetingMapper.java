package io.github.smiskinext.meet.application.mapper;

import io.github.smiskinext.meet.application.result.GetMeetingResult;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.valueobject.JiraIssueLink;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingTimeRange;
import io.github.smiskinext.meet.domain.projection.InviteeSummary;
import io.github.smiskinext.meet.domain.projection.ParticipantSummary;

import java.util.List;

/**
 * Maps a {@link Meeting} aggregate together with its invitee and participant read models into a
 * {@link GetMeetingResult}. The tenant identifier is intentionally omitted.
 */
public final class GetMeetingMapper {

    private GetMeetingMapper() {}

    public static GetMeetingResult toResult(
            Meeting meeting, List<InviteeSummary> invitees, List<ParticipantSummary> participants) {
        return new GetMeetingResult(
                toMeeting(meeting),
                invitees.stream().map(GetMeetingMapper::toInvitee).toList(),
                participants.stream().map(GetMeetingMapper::toParticipant).toList());
    }

    private static GetMeetingResult.Meeting toMeeting(Meeting meeting) {
        JiraIssueLink link = meeting.getIssueLink();
        MeetingSettings settings = meeting.getSettings();
        return new GetMeetingResult.Meeting(
                meeting.getId().value(),
                meeting.getHostId().value(),
                meeting.getShortCode().value(),
                meeting.getType().name(),
                meeting.getStatus().name(),
                meeting.getTitle().value(),
                meeting.getDescription(),
                new GetMeetingResult.IssueLink(link.issueId(), link.issueKey(), link.projectKey()),
                new GetMeetingResult.Settings(
                        settings.admissionPolicy().name(),
                        settings.maxParticipants(),
                        settings.allowScreenShare(),
                        settings.chatEnabled(),
                        settings.allowMicrophone(),
                        settings.allowVideo()),
                meeting.getTimeRange().map(MeetingTimeRange::start).orElse(null),
                meeting.getEndTime().orElse(null),
                meeting.getTimeZone().value(),
                meeting.getOrganizerEmail().value(),
                meeting.getOrganizerDisplayName().value(),
                meeting.getCalendarUid(),
                meeting.getCalendarSequence(),
                meeting.getCreatedAt());
    }

    private static GetMeetingResult.Invitee toInvitee(InviteeSummary summary) {
        return new GetMeetingResult.Invitee(
                summary.accountId(),
                summary.email(),
                summary.displayName(),
                summary.status(),
                summary.invitedAt(),
                summary.respondedAt());
    }

    private static GetMeetingResult.Participant toParticipant(ParticipantSummary summary) {
        return new GetMeetingResult.Participant(
                summary.accountId(),
                summary.displayName(),
                summary.role(),
                summary.joinedAt(),
                summary.leftAt());
    }
}
