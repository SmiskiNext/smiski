package io.github.smiskinext.meet.application.mapper;

import io.github.smiskinext.meet.application.result.ListMeetingsResult;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meet.domain.projection.MeetingSummary;

/**
 * Maps a {@link MeetingSummary} read model into a {@link ListMeetingsResult.Item}.
 */
public final class MeetingSummaryMapper {

    private MeetingSummaryMapper() {}

    public static ListMeetingsResult.Item toItem(MeetingSummary summary) {
        MeetingSettings settings = summary.settings();
        ListMeetingsResult.Item.Settings settingsView = new ListMeetingsResult.Item.Settings(
                settings.admissionPolicy().name(),
                settings.maxParticipants(),
                settings.allowScreenShare(),
                settings.chatEnabled(),
                settings.allowMicrophone(),
                settings.allowVideo());

        return new ListMeetingsResult.Item(
                summary.id(),
                summary.hostId(),
                summary.shortCode(),
                summary.title(),
                summary.description(),
                summary.issueKey(),
                summary.type(),
                summary.status(),
                summary.startTime(),
                summary.endTime(),
                summary.createdAt(),
                settingsView);
    }
}
