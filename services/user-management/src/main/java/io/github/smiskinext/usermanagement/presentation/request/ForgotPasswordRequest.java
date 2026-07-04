package io.github.smiskinext.usermanagement.presentation.request;

import io.github.smiskinext.usermanagement.application.command.RequestPasswordResetCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/**
 * Request DTO for the forgot-password endpoint.
 */
public record ForgotPasswordRequest(
        @NotBlank @Email String email, @Nullable String captchaToken) {

    public RequestPasswordResetCommand toCommand(String ipAddress) {
        return new RequestPasswordResetCommand(email, ipAddress, captchaToken);
    }
}
