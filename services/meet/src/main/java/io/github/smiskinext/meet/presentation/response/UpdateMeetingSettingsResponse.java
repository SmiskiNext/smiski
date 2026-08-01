package io.github.smiskinext.meet.presentation.response;

import io.github.smiskinext.meet.application.result.UpdateMeetingSettingsResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Updated meeting settings snapshot")
public record UpdateMeetingSettingsResponse(
        UUID meetingId,
        String admissionPolicy,
        int maxParticipants,
        boolean allowScreenShare,
        boolean chatEnabled,
        boolean allowMicrophone,
        boolean allowVideo) {

    public static UpdateMeetingSettingsResponse from(UpdateMeetingSettingsResult result) {
        return new UpdateMeetingSettingsResponse(
                result.meetingId(),
                result.admissionPolicy(),
                result.maxParticipants(),
                result.allowScreenShare(),
                result.chatEnabled(),
                result.allowMicrophone(),
                result.allowVideo());
    }
}
