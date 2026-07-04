package io.github.smiskinext.chatmanagement.application.usecase;

import io.github.smiskinext.chatmanagement.application.port.ChatRoomRepository;
import io.github.smiskinext.chatmanagement.domain.model.ChatError;
import io.github.smiskinext.chatmanagement.domain.model.ChatRoom;
import io.github.phunguy65.zms.shared.domain.Result;
import org.springframework.stereotype.Service;

/** Retrieves room info. */
@Service
public class GetRoomUseCase {

    private final ChatRoomRepository chatRoomRepository;

    public GetRoomUseCase(ChatRoomRepository chatRoomRepository) {
        this.chatRoomRepository = chatRoomRepository;
    }

    /**
     * Returns the chat room for the given identifier, or {@link ChatError.RoomNotFound} if not found.
     */
    public Result<ChatRoom, ChatError> execute(String roomId) {
        return chatRoomRepository
                .findByRoomId(roomId)
                .<Result<ChatRoom, ChatError>>map(Result::success)
                .orElseGet(() -> Result.failure(new ChatError.RoomNotFound(roomId)));
    }
}
