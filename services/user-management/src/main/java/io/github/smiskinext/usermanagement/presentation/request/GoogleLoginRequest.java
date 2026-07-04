package io.github.smiskinext.usermanagement.presentation.request;

import io.github.smiskinext.usermanagement.application.command.GoogleLoginCommand;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/auth/google-login}.
 */
public record GoogleLoginRequest(@NotBlank String idToken) {

    public GoogleLoginCommand toCommand() {
        return new GoogleLoginCommand(idToken);
    }
}
