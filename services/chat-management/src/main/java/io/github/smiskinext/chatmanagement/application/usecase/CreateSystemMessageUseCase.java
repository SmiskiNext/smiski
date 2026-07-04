package io.github.smiskinext.chatmanagement.application.usecase;

import io.github.smiskinext.chatmanagement.application.port.ChatLiveKitPort;
import io.github.smiskinext.chatmanagement.application.port.ChatMessageRepository;
import io.github.smiskinext.chatmanagement.application.port.ChatRoomRepository;
import io.github.smiskinext.chatmanagement.application.port.SeqCounter;
import io.github.smiskinext.chatmanagement.domain.model.ChatMessage;
import io.github.smiskinext.chatmanagement.domain.model.ChatRoom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Creates a system message in a chat room (join/leave/kick). */
@Service
public class CreateSystemMessageUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateSystemMessageUseCase.class);

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatLiveKitPort chatLiveKitPort;
    private final SeqCounter seqCounter;

    public CreateSystemMessageUseCase(
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository,
            ChatLiveKitPort chatLiveKitPort,
            SeqCounter seqCounter) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatLiveKitPort = chatLiveKitPort;
        this.seqCounter = seqCounter;
    }

    /**
     * Creates a system message in the given room and broadcasts it.
     *
     * <p>No-op if the room does not exist or is not ACTIVE.
     *
     * @param roomId  the chat room identifier
     * @param content the system message content (e.g. "{displayName} đã tham gia cuộc họp")
     */
    public void execute(String roomId, String content) {
        var room = chatRoomRepository.findByRoomId(roomId).orElse(null);
        if (room == null) {
            log.debug("System message skipped: room {} not found", roomId);
            return;
        }
        if (!ChatRoom.RoomStatus.ACTIVE.equals(room.getStatus())) {
            log.debug(
                    "System message skipped: room {} is not active (status={})",
                    roomId,
                    room.getStatus());
            return;
        }

        long seqNum = seqCounter.nextSeq(roomId);
        ChatMessage message = ChatMessage.systemMessage(seqNum, roomId, content);
        ChatMessage saved = chatMessageRepository.save(message);
        chatLiveKitPort.broadcastMessage(roomId, saved);
        log.debug("System message {} created and broadcast for room {}", seqNum, roomId);
    }
}
