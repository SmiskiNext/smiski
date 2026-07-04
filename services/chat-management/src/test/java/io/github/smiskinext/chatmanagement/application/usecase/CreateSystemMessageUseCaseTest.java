package io.github.smiskinext.chatmanagement.application.usecase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.chatmanagement.application.port.ChatLiveKitPort;
import io.github.smiskinext.chatmanagement.application.port.ChatMessageRepository;
import io.github.smiskinext.chatmanagement.application.port.ChatRoomRepository;
import io.github.smiskinext.chatmanagement.application.port.SeqCounter;
import io.github.smiskinext.chatmanagement.domain.model.ChatMessage;
import io.github.smiskinext.chatmanagement.domain.model.ChatRoom;
import io.github.phunguy65.zms.shared.domain.Result;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateSystemMessageUseCaseTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatLiveKitPort chatLiveKitPort;

    @Mock
    private SeqCounter seqCounter;

    private CreateSystemMessageUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateSystemMessageUseCase(
                chatRoomRepository, chatMessageRepository, chatLiveKitPort, seqCounter);
    }

    @Test
    void execute_roomNotFound_noOps() {
        String roomId = "nonexistent";
        when(chatRoomRepository.findByRoomId(roomId)).thenReturn(Optional.empty());

        useCase.execute(roomId, "Alice đã tham gia cuộc họp");

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void execute_roomNotActive_noOps() {
        String roomId = "meeting-123";
        ChatRoom room = new ChatRoom();
        room.setStatus(ChatRoom.RoomStatus.ARCHIVED);

        when(chatRoomRepository.findByRoomId(roomId)).thenReturn(Optional.of(room));

        useCase.execute(roomId, "Alice đã tham gia cuộc họp");

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void execute_roomActive_createsAndBroadcastsSystemMessage() {
        String roomId = "meeting-123";
        ChatRoom room = new ChatRoom();
        room.setStatus(ChatRoom.RoomStatus.ACTIVE);

        ChatMessage saved = ChatMessage.systemMessage(1L, roomId, "Alice đã tham gia cuộc họp");

        when(chatRoomRepository.findByRoomId(roomId)).thenReturn(Optional.of(room));
        when(seqCounter.nextSeq(roomId)).thenReturn(1L);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(saved);
        when(chatLiveKitPort.broadcastMessage(eq(roomId), any(ChatMessage.class)))
                .thenReturn(Result.success());

        useCase.execute(roomId, "Alice đã tham gia cuộc họp");

        verify(chatMessageRepository).save(any(ChatMessage.class));
        verify(chatLiveKitPort).broadcastMessage(eq(roomId), any(ChatMessage.class));
    }
}
