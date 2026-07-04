package io.github.smiskinext.chatmanagement.application.usecase;

import io.github.smiskinext.chatmanagement.application.port.ChatLiveKitPort;
import io.github.smiskinext.chatmanagement.application.port.ChatMessageRepository;
import io.github.smiskinext.chatmanagement.application.port.ChatRoomRepository;
import io.github.smiskinext.chatmanagement.application.port.SeqCounter;
import io.github.smiskinext.chatmanagement.domain.model.ChatError;
import io.github.smiskinext.chatmanagement.domain.model.ChatMessage;
import io.github.smiskinext.chatmanagement.domain.model.ChatRoom;
import io.github.smiskinext.chatmanagement.domain.port.ChatLimitsPort;
import io.github.phunguy65.zms.shared.domain.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Sends a user message to a chat room. */
@Service
public class SendMessageUseCase {

    private static final Logger log = LoggerFactory.getLogger(SendMessageUseCase.class);

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatLiveKitPort chatLiveKitPort;
    private final ChatLimitsPort chatLimitsPort;
    private final SeqCounter seqCounter;

    public SendMessageUseCase(
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository,
            ChatLiveKitPort chatLiveKitPort,
            ChatLimitsPort chatLimitsPort,
            SeqCounter seqCounter) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatLiveKitPort = chatLiveKitPort;
        this.chatLimitsPort = chatLimitsPort;
        this.seqCounter = seqCounter;
    }

    /**
     * Sends a text message from a user into a chat room.
     *
     * <p>Validation chain:
     * <ol>
     *   <li>Room exists → {@link ChatError.RoomNotFound}
     *   <li>Room is ACTIVE → {@link ChatError.Unauthorized}
     *   <li>Content ≤ max length → {@link ChatError.MessageTooLong}
     * </ol>
     *
     * <p>MongoDB is the source of truth — the message is saved before attempting LiveKit broadcast.
     * If broadcast fails (non-KNPE error), the message is still persisted.
     */
    public Result<ChatMessage, ChatError> execute(
            String roomId, String senderId, String senderName, String content, Long replyToSeqNum) {

        // 1. Room must exist
        ChatRoom room = chatRoomRepository.findByRoomId(roomId).orElse(null);
        if (room == null) {
            return Result.failure(new ChatError.RoomNotFound(roomId));
        }

        // 2. Room must be ACTIVE
        if (!ChatRoom.RoomStatus.ACTIVE.equals(room.getStatus())) {
            return Result.failure(new ChatError.Unauthorized("Meeting has ended"));
        }

        // 3. Content length
        int maxLength = chatLimitsPort.getMaxMessageLength();
        if (content != null && content.length() > maxLength) {
            return Result.failure(new ChatError.MessageTooLong(maxLength, content.length()));
        }

        // 4. Save to MongoDB (source of truth)
        long seqNum = seqCounter.nextSeq(roomId);
        ChatMessage message =
                ChatMessage.send(seqNum, roomId, senderId, senderName, content, replyToSeqNum);
        ChatMessage saved = chatMessageRepository.save(message);

        // 5. Broadcast via LiveKit — non-blocking; MongoDB is authoritative
        chatLiveKitPort.broadcastMessage(roomId, saved);

        log.debug("Message {} saved and broadcast for room {}", saved.getSeqNum(), roomId);
        return Result.success(saved);
    }
}
