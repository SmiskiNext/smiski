package io.github.smiskinext.meetingmanagement.presentation.request;

import io.github.smiskinext.meetingmanagement.application.command.RequestJoinCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record JoinRequestRequest(
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank String deviceId,
        @Nullable @Size(max = 128) String password) {

    public RequestJoinCommand toCommand(UUID meetingId, @Nullable UUID userId) {
        return new RequestJoinCommand(meetingId, userId, displayName, deviceId, password);
    }
}
