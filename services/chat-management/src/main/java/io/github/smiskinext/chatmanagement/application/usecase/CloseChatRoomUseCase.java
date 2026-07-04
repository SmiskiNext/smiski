package io.github.smiskinext.chatmanagement.application.usecase;

import io.github.smiskinext.chatmanagement.application.port.ChatRoomRepository;
import io.github.smiskinext.chatmanagement.domain.model.ChatRoom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Archives a chat room when the meeting ends. */
@Service
public class CloseChatRoomUseCase {

    private static final Logger log = LoggerFactory.getLogger(CloseChatRoomUseCase.class);

    private final ChatRoomRepository chatRoomRepository;

    public CloseChatRoomUseCase(ChatRoomRepository chatRoomRepository) {
        this.chatRoomRepository = chatRoomRepository;
    }

    /**
     * Archives the chat room for the given meeting by setting its status to {@code ARCHIVED}.
     *
     * <p>Idempotent — if the room does not exist (duplicate event delivery), this is a no-op.
     *
     * @param meetingId the meeting UUID as a string
     */
    public void execute(String meetingId) {
        chatRoomRepository
                .findByRoomId(meetingId)
                .ifPresentOrElse(
                        room -> {
                            room.setStatus(ChatRoom.RoomStatus.ARCHIVED);
                            chatRoomRepository.save(room);
                            log.info("Archived chat room for meeting {}", meetingId);
                        },
                        () -> log.debug(
                                "No chat room found for meeting {}, skipping archive", meetingId));
    }
}
