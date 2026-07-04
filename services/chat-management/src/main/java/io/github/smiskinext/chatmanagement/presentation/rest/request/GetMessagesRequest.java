package io.github.smiskinext.chatmanagement.presentation.rest.request;

/**
 * Query parameters for fetching chat messages with cursor pagination.
 */
public record GetMessagesRequest(int size, Long beforeSeqNum) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public GetMessagesRequest {
        if (size <= 0) size = DEFAULT_SIZE;
        if (size > MAX_SIZE) size = MAX_SIZE;
    }
}
