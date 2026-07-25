package io.github.smiskinext.meet.presentation.request;

import io.github.smiskinext.meet.application.command.RequestJoinCommand;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

@Schema(description = "Request body for joining a meeting")
public record JoinMeetingRequest(
        @Schema(description = "Display name shown to the host and in the room", example = "Alice")
        @NotBlank @Size(max = 100) String displayName,

        @Schema(description = "Stable identifier of the joining device", example = "device-1")
        @NotBlank String deviceId,

        @Schema(description = "Avatar URL of the joining participant", nullable = true) @Nullable String avatarUrl) {

    public RequestJoinCommand toCommand(String meetingId, String accountId, String tenantId) {
        return new RequestJoinCommand(
                meetingId, tenantId, accountId, displayName, deviceId, avatarUrl);
    }
}
