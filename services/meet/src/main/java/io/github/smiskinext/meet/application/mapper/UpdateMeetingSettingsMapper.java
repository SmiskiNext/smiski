package io.github.smiskinext.meet.application.mapper;

import io.github.smiskinext.meet.application.result.UpdateMeetingSettingsResult;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;

/**
 * Maps a {@link Meeting} aggregate's settings into an {@link UpdateMeetingSettingsResult}
 * snapshot.
 */
public final class UpdateMeetingSettingsMapper {

    private UpdateMeetingSettingsMapper() {}

    public static UpdateMeetingSettingsResult toResult(Meeting meeting) {
        MeetingSettings settings = meeting.getSettings();
        return new UpdateMeetingSettingsResult(
                meeting.getId().value(),
                settings.admissionPolicy().name(),
                settings.maxParticipants(),
                settings.allowScreenShare(),
                settings.chatEnabled(),
                settings.allowMicrophone(),
                settings.allowVideo());
    }
}
