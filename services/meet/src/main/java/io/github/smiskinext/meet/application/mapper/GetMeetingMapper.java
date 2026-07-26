package io.github.smiskinext.meet.application.mapper;

import io.github.smiskinext.meet.application.result.GetMeetingResult;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meet.domain.projection.InviteeSummary;
import io.github.smiskinext.meet.domain.projection.MeetingDetail;
import io.github.smiskinext.meet.domain.projection.ParticipantSummary;

import java.util.List;

/**
 * Maps a {@link MeetingDetail} projection together with its invitee and participant read models
 * into a {@link GetMeetingResult}. The tenant identifier is intentionally omitted.
 */
public final class GetMeetingMapper {

    private GetMeetingMapper() {}

    public static GetMeetingResult toResult(
            MeetingDetail meeting,
            List<InviteeSummary> invitees,
            List<ParticipantSummary> participants) {
        return new GetMeetingResult(
                toMeeting(meeting),
                invitees.stream().map(GetMeetingMapper::toInvitee).toList(),
                participants.stream().map(GetMeetingMapper::toParticipant).toList());
    }

    private static GetMeetingResult.Meeting toMeeting(MeetingDetail meeting) {
        MeetingSettings settings = meeting.settings();
        return new GetMeetingResult.Meeting(
                meeting.id(),
                meeting.hostId(),
                meeting.shortCode(),
                meeting.type().name(),
                meeting.status().name(),
                meeting.title(),
                meeting.description(),
                new GetMeetingResult.IssueLink(
                        meeting.issueId(), meeting.issueKey(), meeting.projectKey()),
                new GetMeetingResult.Settings(
                        settings.admissionPolicy().name(),
                        settings.maxParticipants(),
                        settings.allowScreenShare(),
                        settings.chatEnabled(),
                        settings.allowMicrophone(),
                        settings.allowVideo()),
                meeting.startTime(),
                meeting.endTime(),
                meeting.zoneId(),
                meeting.organizerEmail(),
                meeting.organizerDisplayName(),
                meeting.calendarUid(),
                meeting.calendarSequence(),
                meeting.createdAt());
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
                summary.accountId(), summary.role(), summary.joinedAt(), summary.leftAt());
    }
}
