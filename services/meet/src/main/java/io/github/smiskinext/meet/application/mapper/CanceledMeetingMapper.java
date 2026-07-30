package io.github.smiskinext.meet.application.mapper;

import io.github.smiskinext.meet.application.result.CancelMeetingResult;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.valueobject.JiraIssueLink;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingTimeRange;

/**
 * Maps a canceled {@link Meeting} aggregate into a {@link CancelMeetingResult} snapshot.
 */
public final class CanceledMeetingMapper {

    private CanceledMeetingMapper() {}

    public static CancelMeetingResult toResult(Meeting meeting) {
        JiraIssueLink link = meeting.getIssueLink();
        MeetingSettings settings = meeting.getSettings();
        return new CancelMeetingResult(
                meeting.getId().value(),
                meeting.getHostId().value(),
                meeting.getShortCode().value(),
                meeting.getType().name(),
                meeting.getStatus().name(),
                meeting.getTitle().value(),
                meeting.getDescription(),
                new CancelMeetingResult.IssueLink(
                        link.issueId(), link.issueKey(), link.projectKey()),
                new CancelMeetingResult.Settings(
                        settings.admissionPolicy().name(),
                        settings.maxParticipants(),
                        settings.allowScreenShare(),
                        settings.chatEnabled(),
                        settings.allowMicrophone(),
                        settings.allowVideo()),
                meeting.getTimeRange().map(MeetingTimeRange::start).orElse(null),
                meeting.getTimeRange().map(MeetingTimeRange::end).orElse(null),
                meeting.getTimeZone().value(),
                meeting.getOrganizerEmail().value(),
                meeting.getOrganizerDisplayName().value(),
                meeting.getCalendarUid(),
                meeting.getCalendarSequence(),
                meeting.getCreatedAt(),
                meeting.getCancelReason().orElseThrow().name(),
                meeting.getUpdatedAt());
    }
}
