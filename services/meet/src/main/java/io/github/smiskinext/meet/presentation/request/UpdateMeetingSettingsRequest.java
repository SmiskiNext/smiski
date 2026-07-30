package io.github.smiskinext.meet.presentation.request;

import io.github.smiskinext.meet.application.command.UpdateMeetingSettingsCommand;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

@Schema(description = "Full settings replacement request")
public record UpdateMeetingSettingsRequest(
        @NotBlank @Pattern(regexp = "ALLOW_ALL|MANUAL_APPROVAL") String admissionPolicy,

        @Min(2) @Max(100) int maxParticipants,
        boolean allowScreenShare,
        boolean chatEnabled,
        boolean allowMicrophone,
        boolean allowVideo) {

    public UpdateMeetingSettingsCommand toCommand(
            UUID meetingId, String accountId, String tenantId) {
        return new UpdateMeetingSettingsCommand(
                meetingId,
                tenantId,
                accountId,
                admissionPolicy,
                maxParticipants,
                allowScreenShare,
                chatEnabled,
                allowMicrophone,
                allowVideo);
    }
}
