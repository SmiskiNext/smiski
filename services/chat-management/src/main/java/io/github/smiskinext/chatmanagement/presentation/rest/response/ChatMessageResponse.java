package io.github.smiskinext.chatmanagement.presentation.rest.response;

import java.time.Instant;

/** JSON response for a chat message. */
public record ChatMessageResponse(
        String id,
        Long seqNum,
        String roomId,
        String senderId,
        String senderName,
        String content,
        String type,
        Instant createdAt) {}
