package io.github.smiskinext.chatmanagement.application.port;

import io.github.smiskinext.chatmanagement.domain.model.ChatMessage;
import io.github.smiskinext.shared.domain.CursorPageResponse;
import java.util.Optional;

/** Outbound port: persistence operations for {@link ChatMessage}. */
public interface ChatMessageRepository {

    /** Persists or updates a chat message. */
    ChatMessage save(ChatMessage message);

    /**
     * Returns a cursor-scrolled page of messages for the given room.
     *
     * <p>Results are ordered by {@code seqNum DESC}. When {@code beforeSeqNum} is provided,
     * returns messages with {@code seqNum < beforeSeqNum} for backward scrolling.
     *
     * @param roomId        the chat room identifier
     * @param pageSize      maximum number of items to return
     * @param beforeSeqNum  the exclusive upper bound seqNum for cursor pagination;
     *                      empty for the first (most recent) page
     */
    CursorPageResponse<ChatMessage> findByRoomId(
            String roomId, int pageSize, Optional<Long> beforeSeqNum);
}
