package io.github.smiskinext.chatmanagement.application.usecase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.chatmanagement.application.port.ChatRoomRepository;
import io.github.smiskinext.chatmanagement.domain.model.ChatRoom;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CloseChatRoomUseCaseTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    private CloseChatRoomUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CloseChatRoomUseCase(chatRoomRepository);
    }

    @Test
    void execute_roomExists_setsStatusToArchivedAndSaves() {
        String meetingId = "meeting-123";
        ChatRoom room = new ChatRoom();
        room.setStatus(ChatRoom.RoomStatus.ACTIVE);

        when(chatRoomRepository.findByRoomId(meetingId)).thenReturn(Optional.of(room));

        useCase.execute(meetingId);

        ArgumentCaptor<ChatRoom> captor = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomRepository).save(captor.capture());
        assert captor.getValue().getStatus() == ChatRoom.RoomStatus.ARCHIVED;
    }

    @Test
    void execute_roomNotFound_isIdempotentNoOp() {
        String meetingId = "nonexistent";
        when(chatRoomRepository.findByRoomId(meetingId)).thenReturn(Optional.empty());

        useCase.execute(meetingId);

        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
    }

    @Test
    void execute_roomAlreadyArchived_stillSaves() {
        String meetingId = "meeting-456";
        ChatRoom room = new ChatRoom();
        room.setStatus(ChatRoom.RoomStatus.ARCHIVED);

        when(chatRoomRepository.findByRoomId(meetingId)).thenReturn(Optional.of(room));

        useCase.execute(meetingId);

        verify(chatRoomRepository).save(any(ChatRoom.class));
    }
}
