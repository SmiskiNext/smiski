package io.github.smiskinext.usermanagement.domain;

import io.github.smiskinext.shared.domain.DomainError;

/**
 * Typed domain errors for all authentication and user-management failures.
 *
 * <p>Use cases return {@code Result<T, AuthError>} instead of throwing exceptions for business-rule
 * violations. Controllers pattern-match on the concrete record type to determine the HTTP status.
 */
public sealed interface AuthError extends DomainError {

    /** Registration attempt with an email address already in use. */
    record EmailAlreadyExists() implements AuthError {
        @Override
        public String message() {
            return "Email address is already in use";
        }
    }

    /** Registration or update attempt with a username already taken by an active user. */
    record UsernameAlreadyExists() implements AuthError {
        @Override
        public String message() {
            return "Username is already taken";
        }
    }

    /** Login failed due to wrong email or password (no user enumeration). */
    record InvalidCredentials() implements AuthError {
        @Override
        public String message() {
            return "Invalid email or password";
        }
    }

    /** Refresh / logout token not found by its SHA-256 hash. */
    record RefreshTokenNotFound() implements AuthError {
        @Override
        public String message() {
            return "Refresh token not found";
        }
    }

    /** Refresh token has passed its {@code expires_at} timestamp. */
    record RefreshTokenExpired() implements AuthError {
        @Override
        public String message() {
            return "Refresh token has expired";
        }
    }

    /** Refresh token has already been revoked. */
    record RefreshTokenRevoked() implements AuthError {
        @Override
        public String message() {
            return "Refresh token has been revoked";
        }
    }

    /** A previously revoked token was presented — possible token theft detected. */
    record RefreshTokenReuseDetected() implements AuthError {
        @Override
        public String message() {
            return "Refresh token reuse detected — all sessions have been invalidated";
        }
    }

    /** Operation requires a user that does not exist. */
    record UserNotFound() implements AuthError {
        @Override
        public String message() {
            return "User not found";
        }
    }

    /** Deleted user attempted login or JWT check failed because account is soft-deleted. */
    record UserDeleted() implements AuthError {
        @Override
        public String message() {
            return "This account has been deleted";
        }
    }

    /** Firebase ID token failed verification (expired, malformed, wrong audience, etc.). */
    record InvalidFirebaseToken() implements AuthError {
        @Override
        public String message() {
            return "Firebase ID token is invalid or expired";
        }
    }

    /** Firebase Admin SDK returned an unexpected error during token verification. */
    record FirebaseAuthError() implements AuthError {
        @Override
        public String message() {
            return "Firebase authentication service is unavailable";
        }
    }

    /** Preferences JSON serialization failed due to an internal/unexpected error. */
    record PreferencesSerializationError() implements AuthError {
        @Override
        public String message() {
            return "Failed to serialize user preferences";
        }
    }

    /** Password reset OTP has expired (past 20-minute window). */
    record OtpExpired() implements AuthError {
        @Override
        public String message() {
            return "Password reset code has expired";
        }
    }

    /** Password reset OTP does not match the stored hash. */
    record OtpInvalid() implements AuthError {
        @Override
        public String message() {
            return "Invalid password reset code";
        }
    }

    /** Password reset OTP has already been used. */
    record OtpAlreadyUsed() implements AuthError {
        @Override
        public String message() {
            return "Password reset code has already been used";
        }
    }

    /** Too many wrong OTP attempts — token is locked. */
    record OtpLocked() implements AuthError {
        @Override
        public String message() {
            return "Too many failed attempts. Please request a new code.";
        }
    }

    /** Password reset attempted for a Google-only account (no password set). */
    record GoogleOnlyAccount() implements AuthError {
        @Override
        public String message() {
            return "This account uses Google Sign-In. Please sign in with Google.";
        }
    }

    /** CAPTCHA verification is required after multiple failed attempts. */
    record CaptchaRequired() implements AuthError {
        @Override
        public String message() {
            return "CAPTCHA verification required. Please complete the CAPTCHA.";
        }
    }

    /** CAPTCHA token verification failed. */
    record CaptchaInvalid() implements AuthError {
        @Override
        public String message() {
            return "CAPTCHA verification failed. Please try again.";
        }
    }

    /** Password reset resend attempted too soon (cooldown not elapsed). */
    record ResendTooSoon(long retryAfterSeconds) implements AuthError {
        @Override
        public String message() {
            return "Please wait " + retryAfterSeconds + " seconds before requesting a new code.";
        }
    }

    /** Temporary token has expired. */
    record TokenExpired() implements AuthError {
        @Override
        public String message() {
            return "Temporary token has expired. Please verify OTP again.";
        }
    }

    /** Temporary token is invalid or malformed. */
    record TokenInvalid() implements AuthError {
        @Override
        public String message() {
            return "Invalid temporary token.";
        }
    }

    /** Temporary token purpose does not match expected purpose. */
    record TokenInvalidPurpose() implements AuthError {
        @Override
        public String message() {
            return "Token cannot be used for this operation.";
        }
    }
}
