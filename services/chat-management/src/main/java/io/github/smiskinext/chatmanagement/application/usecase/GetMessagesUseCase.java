package io.github.smiskinext.chatmanagement.application.usecase;

import io.github.smiskinext.chatmanagement.application.port.ChatMessageRepository;
import io.github.smiskinext.chatmanagement.application.port.ChatRoomRepository;
import io.github.phunguy65.zms.shared.domain.CursorPageResponse;
import java.util.Optional;

import io.github.smiskinext.chatmanagement.domain.model.ChatMessage;
import org.springframework.stereotype.Service;

/** Retrieves a page of messages from a chat room. */
@Service
public class GetMessagesUseCase {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    public GetMessagesUseCase(
            ChatRoomRepository chatRoomRepository, ChatMessageRepository chatMessageRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * Returns a cursor-scrolled page of messages for the given room.
     *
     * @param roomId       the chat room identifier
     * @param pageSize     number of items per page (caller should clamp to [1, 100])
     * @param beforeSeqNum exclusive upper bound seqNum for cursor pagination; empty for first page
     */
    public CursorPageResponse<ChatMessage>
            execute(String roomId, int pageSize, Optional<Long> beforeSeqNum) {

        if (chatRoomRepository.findByRoomId(roomId).isEmpty()) {
            return CursorPageResponse.empty(pageSize);
        }

        return chatMessageRepository.findByRoomId(roomId, pageSize, beforeSeqNum);
    }
}
