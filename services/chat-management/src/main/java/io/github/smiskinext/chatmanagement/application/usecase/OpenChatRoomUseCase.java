package io.github.smiskinext.chatmanagement.application.usecase;

import io.github.smiskinext.chatmanagement.application.port.ChatRoomRepository;
import io.github.smiskinext.chatmanagement.domain.model.ChatRoom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Opens (or idempotently no-ops) a chat room for a meeting. */
@Service
public class OpenChatRoomUseCase {

    private static final Logger log = LoggerFactory.getLogger(OpenChatRoomUseCase.class);

    private final ChatRoomRepository chatRoomRepository;

    public OpenChatRoomUseCase(ChatRoomRepository chatRoomRepository) {
        this.chatRoomRepository = chatRoomRepository;
    }

    /**
     * Creates a chat room for the given meeting, or silently no-ops if one already exists.
     *
     * <p>This operation is idempotent — duplicate {@link io.github.phunguy65.zms.meetingmanagement.domain.event.MeetingStartedEvent}
     * deliveries do not cause errors.
     *
     * @param meetingId the meeting UUID as a string
     */
    public void execute(String meetingId) {
        if (chatRoomRepository.existsByRoomId(meetingId)) {
            log.debug("Chat room already exists for meeting {}, skipping creation", meetingId);
            return;
        }
        var room = ChatRoom.create(meetingId);
        chatRoomRepository.save(room);
        log.info("Created chat room for meeting {}", meetingId);
    }
}
