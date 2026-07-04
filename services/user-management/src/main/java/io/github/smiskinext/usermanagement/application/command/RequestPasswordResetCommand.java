package io.github.smiskinext.usermanagement.application.command;

import org.jspecify.annotations.Nullable;

/**
 * Command to request a password reset OTP.
 *
 * @param email        the email address to send the OTP to
 * @param ipAddress    the client IP address for rate limiting (may be null)
 * @param captchaToken the CAPTCHA token for verification (may be null)
 */
public record RequestPasswordResetCommand(
        String email, @Nullable String ipAddress, @Nullable String captchaToken) {}
