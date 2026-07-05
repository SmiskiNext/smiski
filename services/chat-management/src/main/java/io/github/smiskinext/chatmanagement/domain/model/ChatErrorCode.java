package io.github.smiskinext.chatmanagement.domain.model;

import io.github.smiskinext.shared.domain.ErrorCode;

/**
 * Machine-readable error codes for chat subsystem errors.
 *
 * <p>Used in JSend {@code fail} responses and WebSocket {@code MESSAGE_NACK} frames.
 */
public enum ChatErrorCode implements ErrorCode {
    MESSAGE_TOO_LONG,
    ROOM_NOT_FOUND,
    UNAUTHORIZED,
    PERSISTENCE_FAILURE
}
