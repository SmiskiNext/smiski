package io.github.smiskinext.meet.application.mapper;

import io.github.smiskinext.meet.application.result.DeleteMeetingResult;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.valueobject.JiraIssueLink;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingTimeRange;

/**
 * Maps a soft-deleted {@link Meeting} aggregate into a {@link DeleteMeetingResult} snapshot.
 */
public final class DeletedMeetingMapper {

    private DeletedMeetingMapper() {}

    public static DeleteMeetingResult toResult(Meeting meeting) {
        JiraIssueLink link = meeting.getIssueLink();
        MeetingSettings settings = meeting.getSettings();
        return new DeleteMeetingResult(
                meeting.getId().value(),
                meeting.getHostId().value(),
                meeting.getShortCode().value(),
                meeting.getType().name(),
                meeting.getStatus().name(),
                meeting.getTitle().value(),
                meeting.getDescription(),
                new DeleteMeetingResult.IssueLink(
                        link.issueId(), link.issueKey(), link.projectKey()),
                new DeleteMeetingResult.Settings(
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
                meeting.getDeletedAt().orElseThrow(),
                meeting.getDeletedBy().orElseThrow().value());
    }
}
