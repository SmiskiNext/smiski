package io.github.smiskinext.chatmanagement.domain.model;

import java.time.Instant;

/**
 * Optional metadata attached to a chat message.
 *
 * <p>Currently supports reply threading (referencing another message's {@code seqNum})
 * and edit tracking.
 */
public record MessageMetadata(Long replyToSeqNum, Instant editedAt) {}
