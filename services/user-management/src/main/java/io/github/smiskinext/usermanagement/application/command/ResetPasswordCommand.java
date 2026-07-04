package io.github.smiskinext.usermanagement.application.command;

import org.jspecify.annotations.Nullable;

/**
 * Command to reset a password using an OTP or temporary token.
 *
 * @param email          the email address
 * @param otp            the 6-digit OTP received via email (legacy, optional)
 * @param temporaryToken the temporary token from OTP verification (new, optional)
 * @param newPassword    the new password to set
 */
public record ResetPasswordCommand(
        String email, @Nullable String otp, @Nullable String temporaryToken, String newPassword) {}
