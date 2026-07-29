package io.github.smiskinext.notification.infrastructure.email;

/**
 * Signals that a calendar email could not be delivered, triggering consumer retry and dead-letter
 * handling.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
