package io.github.smiskinext.meet.application.mapper;

import io.github.smiskinext.meet.application.result.EndMeetingResult;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.valueobject.JiraIssueLink;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingTimeRange;

/**
 * Maps a completed {@link Meeting} aggregate into an {@link EndMeetingResult} snapshot.
 */
public final class EndedMeetingMapper {

    private EndedMeetingMapper() {}

    public static EndMeetingResult toResult(Meeting meeting) {
        JiraIssueLink link = meeting.getIssueLink();
        MeetingSettings settings = meeting.getSettings();
        return new EndMeetingResult(
                meeting.getId().value(),
                meeting.getHostId().value(),
                meeting.getShortCode().value(),
                meeting.getType().name(),
                meeting.getStatus().name(),
                meeting.getCancelReason().map(Enum::name).orElse(null),
                meeting.getTitle().value(),
                meeting.getDescription(),
                new EndMeetingResult.IssueLink(link.issueId(), link.issueKey(), link.projectKey()),
                new EndMeetingResult.Settings(
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
}
