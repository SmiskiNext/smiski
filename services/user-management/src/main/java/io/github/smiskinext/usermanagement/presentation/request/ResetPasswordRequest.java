package io.github.smiskinext.usermanagement.presentation.request;

import io.github.smiskinext.usermanagement.application.command.ResetPasswordCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Request DTO for the reset-password endpoint.
 * Supports both legacy OTP and new temporary token for 30-day grace period.
 */
public record ResetPasswordRequest(
        @NotBlank @Email String email,

        @Nullable @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits") String otp,

        @Nullable String temporaryToken,

        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String newPassword) {

    public ResetPasswordCommand toCommand() {
        return new ResetPasswordCommand(email, otp, temporaryToken, newPassword);
    }
}
