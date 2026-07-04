package io.github.smiskinext.chatmanagement.domain.model;

import io.github.phunguy65.zms.shared.domain.DomainError;

/**
 * Domain-level errors for the chat subsystem.
 *
 * <p>Each variant carries structured data used by {@link ChatErrorCode} and the
 * presentation layer to produce appropriate HTTP responses or WebSocket frames.
 */
public sealed interface ChatError extends DomainError {

    record MessageTooLong(int maxLength, int actualLength) implements ChatError {
        @Override
        public String message() {
            return "Message exceeds maximum length of " + maxLength + " characters (actual: "
                    + actualLength + ")";
        }
    }

    record RoomNotFound(String roomId) implements ChatError {
        @Override
        public String message() {
            return "Room not found: " + roomId;
        }
    }

    record Unauthorized(String reason) implements ChatError {
        @Override
        public String message() {
            return "Unauthorized: " + reason;
        }
    }

    record PersistenceFailure(String detail) implements ChatError {
        @Override
        public String message() {
            return "Persistence failure: " + detail;
        }
    }
}
