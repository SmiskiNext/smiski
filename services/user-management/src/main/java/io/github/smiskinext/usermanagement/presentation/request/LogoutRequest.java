package io.github.smiskinext.usermanagement.presentation.request;

import io.github.smiskinext.usermanagement.application.command.LogoutCommand;
import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(@NotBlank String refreshToken) {

    public LogoutCommand toCommand() {
        return new LogoutCommand(refreshToken);
    }
}
