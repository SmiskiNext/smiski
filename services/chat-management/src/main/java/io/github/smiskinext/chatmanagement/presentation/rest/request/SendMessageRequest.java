package io.github.smiskinext.chatmanagement.presentation.rest.request;

/**
 * Request body for sending a chat message.
 *
 * <p>Validation is done at the use case layer.
 */
public record SendMessageRequest(String senderName, String content, Long replyToSeqNum) {}
