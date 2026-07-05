package io.github.smiskinext.usermanagement.domain;

import io.github.smiskinext.shared.domain.ErrorCode;

/**
 * Machine-readable error codes for all authentication and user-management failures.
 *
 * <p>These constants are used as the {@code code} field in JSend {@code fail} responses.
 * The mapping from {@link AuthError} sealed records to these codes lives in
 * {@code BaseController#errorResponse}.
 */
public enum AuthErrorCode implements ErrorCode {
    EMAIL_ALREADY_EXISTS,
    USERNAME_ALREADY_EXISTS,
    INVALID_CREDENTIALS,
    REFRESH_TOKEN_NOT_FOUND,
    REFRESH_TOKEN_EXPIRED,
    REFRESH_TOKEN_REVOKED,
    REFRESH_TOKEN_REUSE_DETECTED,
    USER_NOT_FOUND,
    USER_DELETED,
    INVALID_FIREBASE_TOKEN,
    FIREBASE_AUTH_ERROR,
    PREFERENCES_SERIALIZATION_ERROR,
    OTP_EXPIRED,
    OTP_INVALID,
    OTP_ALREADY_USED,
    OTP_LOCKED,
    GOOGLE_ONLY_ACCOUNT,
    CAPTCHA_REQUIRED,
    CAPTCHA_INVALID,
    RESEND_TOO_SOON,
    TOKEN_EXPIRED,
    TOKEN_INVALID,
    TOKEN_INVALID_PURPOSE
}
