package io.github.smiskinext.chatmanagement.application.port;

import io.github.smiskinext.chatmanagement.domain.model.ChatRoom;
import java.util.Optional;

/** Outbound port: persistence operations for {@link ChatRoom}. */
public interface ChatRoomRepository {

    /** Persists or updates a chat room. */
    ChatRoom save(ChatRoom room);

    /** Finds a chat room by its unique room identifier (meeting ID). */
    Optional<ChatRoom> findByRoomId(String roomId);

    /** Returns {@code true} if a chat room with the given room identifier already exists. */
    boolean existsByRoomId(String roomId);
}
