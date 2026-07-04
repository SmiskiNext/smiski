package io.github.smiskinext.chatmanagement.application.usecase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.chatmanagement.application.port.ChatRoomRepository;
import io.github.smiskinext.chatmanagement.domain.model.ChatRoom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenChatRoomUseCaseTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    private OpenChatRoomUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new OpenChatRoomUseCase(chatRoomRepository);
    }

    @Test
    void execute_createsNewRoomWhenNotExists() {
        String meetingId = "meeting-123";
        when(chatRoomRepository.existsByRoomId(meetingId)).thenReturn(false);

        useCase.execute(meetingId);

        verify(chatRoomRepository).save(any(ChatRoom.class));
    }

    @Test
    void execute_idempotentWhenRoomAlreadyExists() {
        String meetingId = "meeting-456";
        when(chatRoomRepository.existsByRoomId(meetingId)).thenReturn(true);

        useCase.execute(meetingId);

        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
    }

    @Test
    void execute_createsRoomWithActiveStatus() {
        String meetingId = "meeting-789";
        when(chatRoomRepository.existsByRoomId(meetingId)).thenReturn(false);

        useCase.execute(meetingId);

        verify(chatRoomRepository)
                .save(argThat(room -> ChatRoom.RoomStatus.ACTIVE.equals(room.getStatus())
                        && meetingId.equals(room.getRoomId())
                        && meetingId.equals(room.getMeetingId())));
    }
}
