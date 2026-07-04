package io.github.smiskinext.usermanagement.presentation.request;

import io.github.smiskinext.usermanagement.application.command.VerifyOtpCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,

        @NotBlank(message = "OTP is required") @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits") String otp) {

    public VerifyOtpCommand toCommand() {
        return new VerifyOtpCommand(email, otp);
    }
}
