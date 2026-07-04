package io.github.smiskinext.usermanagement.presentation;

import io.github.phunguy65.zms.shared.infrastructure.web.CommonErrorCode;
import io.github.phunguy65.zms.shared.infrastructure.web.FailData;
import io.github.phunguy65.zms.shared.infrastructure.web.JsendResponse;
import io.github.phunguy65.zms.shared.infrastructure.web.Violation;
import io.github.phunguy65.zms.shared.infrastructure.web.ViolationCode;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.AuthErrorCode;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Shared controller base that centralises the {@code AuthError} → HTTP status and error code
 * mapping, eliminating boilerplate {@link FailData} construction from every endpoint.
 */
abstract class BaseController {

    @SuppressWarnings("unchecked")
    protected <T> ResponseEntity<JsendResponse<T>> errorResponse(AuthError error) {
        HttpStatus status =
                switch (error) {
                    case AuthError.UserNotFound e -> HttpStatus.NOT_FOUND;
                    case AuthError.UserDeleted e -> HttpStatus.UNAUTHORIZED;
                    case AuthError.EmailAlreadyExists e -> HttpStatus.CONFLICT;
                    case AuthError.UsernameAlreadyExists e -> HttpStatus.CONFLICT;
                    case AuthError.InvalidCredentials e -> HttpStatus.UNAUTHORIZED;
                    case AuthError.RefreshTokenNotFound e -> HttpStatus.UNAUTHORIZED;
                    case AuthError.RefreshTokenExpired e -> HttpStatus.UNAUTHORIZED;
                    case AuthError.RefreshTokenRevoked e -> HttpStatus.UNAUTHORIZED;
                    case AuthError.RefreshTokenReuseDetected e -> HttpStatus.UNAUTHORIZED;
                    case AuthError.InvalidFirebaseToken e -> HttpStatus.UNAUTHORIZED;
                    case AuthError.FirebaseAuthError e -> HttpStatus.SERVICE_UNAVAILABLE;
                    case AuthError.PreferencesSerializationError e ->
                        HttpStatus.INTERNAL_SERVER_ERROR;
                    case AuthError.OtpExpired e -> HttpStatus.BAD_REQUEST;
                    case AuthError.OtpInvalid e -> HttpStatus.BAD_REQUEST;
                    case AuthError.OtpAlreadyUsed e -> HttpStatus.BAD_REQUEST;
                    case AuthError.OtpLocked e -> HttpStatus.BAD_REQUEST;
                    case AuthError.GoogleOnlyAccount e -> HttpStatus.BAD_REQUEST;
                    case AuthError.CaptchaRequired e -> HttpStatus.BAD_REQUEST;
                    case AuthError.CaptchaInvalid e -> HttpStatus.BAD_REQUEST;
                    case AuthError.ResendTooSoon e -> HttpStatus.TOO_MANY_REQUESTS;
                    case AuthError.TokenExpired e -> HttpStatus.BAD_REQUEST;
                    case AuthError.TokenInvalid e -> HttpStatus.BAD_REQUEST;
                    case AuthError.TokenInvalidPurpose e -> HttpStatus.BAD_REQUEST;
                };
        AuthErrorCode code =
                switch (error) {
                    case AuthError.UserNotFound e -> AuthErrorCode.USER_NOT_FOUND;
                    case AuthError.UserDeleted e -> AuthErrorCode.USER_DELETED;
                    case AuthError.EmailAlreadyExists e -> AuthErrorCode.EMAIL_ALREADY_EXISTS;
                    case AuthError.UsernameAlreadyExists e -> AuthErrorCode.USERNAME_ALREADY_EXISTS;
                    case AuthError.InvalidCredentials e -> AuthErrorCode.INVALID_CREDENTIALS;
                    case AuthError.RefreshTokenNotFound e -> AuthErrorCode.REFRESH_TOKEN_NOT_FOUND;
                    case AuthError.RefreshTokenExpired e -> AuthErrorCode.REFRESH_TOKEN_EXPIRED;
                    case AuthError.RefreshTokenRevoked e -> AuthErrorCode.REFRESH_TOKEN_REVOKED;
                    case AuthError.RefreshTokenReuseDetected e ->
                        AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED;
                    case AuthError.InvalidFirebaseToken e -> AuthErrorCode.INVALID_FIREBASE_TOKEN;
                    case AuthError.FirebaseAuthError e -> AuthErrorCode.FIREBASE_AUTH_ERROR;
                    case AuthError.PreferencesSerializationError e ->
                        AuthErrorCode.PREFERENCES_SERIALIZATION_ERROR;
                    case AuthError.OtpExpired e -> AuthErrorCode.OTP_EXPIRED;
                    case AuthError.OtpInvalid e -> AuthErrorCode.OTP_INVALID;
                    case AuthError.OtpAlreadyUsed e -> AuthErrorCode.OTP_ALREADY_USED;
                    case AuthError.OtpLocked e -> AuthErrorCode.OTP_LOCKED;
                    case AuthError.GoogleOnlyAccount e -> AuthErrorCode.GOOGLE_ONLY_ACCOUNT;
                    case AuthError.CaptchaRequired e -> AuthErrorCode.CAPTCHA_REQUIRED;
                    case AuthError.CaptchaInvalid e -> AuthErrorCode.CAPTCHA_INVALID;
                    case AuthError.ResendTooSoon e -> AuthErrorCode.RESEND_TOO_SOON;
                    case AuthError.TokenExpired e -> AuthErrorCode.TOKEN_EXPIRED;
                    case AuthError.TokenInvalid e -> AuthErrorCode.TOKEN_INVALID;
                    case AuthError.TokenInvalidPurpose e -> AuthErrorCode.TOKEN_INVALID_PURPOSE;
                };

        if (error instanceof AuthError.ResendTooSoon resendError) {
            return (ResponseEntity<JsendResponse<T>>) (ResponseEntity<?>) ResponseEntity.status(
                            status)
                    .header("Retry-After", String.valueOf(resendError.retryAfterSeconds()))
                    .body(JsendResponse.fail(new FailData(error.message(), code, List.of())));
        }

        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            return (ResponseEntity<JsendResponse<T>>) (ResponseEntity<?>)
                    ResponseEntity.status(status).body(JsendResponse.error(error.message()));
        }
        return (ResponseEntity<JsendResponse<T>>) (ResponseEntity<?>) ResponseEntity.status(status)
                .body(JsendResponse.fail(new FailData(error.message(), code, List.of())));
    }

    @SuppressWarnings("unchecked")
    protected <T> ResponseEntity<JsendResponse<T>> validationErrorResponse(
            String field, String message) {
        FailData body = new FailData(
                "Validation failed",
                CommonErrorCode.VALIDATION_ERROR,
                List.of(new Violation(field, message, ViolationCode.REQUIRED)));
        return (ResponseEntity<JsendResponse<T>>)
                (ResponseEntity<?>) ResponseEntity.badRequest().body(JsendResponse.fail(body));
    }
}
