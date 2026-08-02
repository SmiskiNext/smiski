package io.github.smiskinext.notification.domain;

import io.github.smiskinext.shared.domain.DomainError;
import io.github.smiskinext.shared.domain.ErrorCode;

/**
 * Domain errors for the notification bounded context.
 *
 * <p>Each variant is a pure value exposing its {@link ErrorCode} and the positional arguments used
 * to interpolate the localized {@code detail} message. No human-readable text lives here; it is
 * resolved from the message bundle at the HTTP boundary.
 */
public sealed interface NotificationError extends DomainError {

    /**
     * The Svix webhook signature presented with the inbound email request did not match the
     * expected signature for the configured signing secret.
     */
    record InvalidSignature() implements NotificationError {
        @Override
        public ErrorCode errorCode() {
            return NotificationErrorCode.INVALID_SIGNATURE;
        }
    }
}
