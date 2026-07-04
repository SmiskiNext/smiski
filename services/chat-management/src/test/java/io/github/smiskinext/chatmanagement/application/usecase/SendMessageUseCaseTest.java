package io.github.smiskinext.chatmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.chatmanagement.application.port.ChatLiveKitPort;
import io.github.smiskinext.chatmanagement.application.port.ChatMessageRepository;
import io.github.smiskinext.chatmanagement.application.port.ChatRoomRepository;
import io.github.smiskinext.chatmanagement.application.port.SeqCounter;
import io.github.smiskinext.chatmanagement.domain.model.ChatError;
import io.github.smiskinext.chatmanagement.domain.model.ChatMessage;
import io.github.smiskinext.chatmanagement.domain.model.ChatRoom;
import io.github.smiskinext.chatmanagement.domain.port.ChatLimitsPort;
import io.github.phunguy65.zms.shared.domain.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SendMessageUseCaseTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatLiveKitPort chatLiveKitPort;

    @Mock
    private SeqCounter seqCounter;

    @Mock
    private ChatLimitsPort chatLimitsPort;

    private SendMessageUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SendMessageUseCase(
                chatRoomRepository,
                chatMessageRepository,
                chatLiveKitPort,
                chatLimitsPort,
                seqCounter);
    }

    @Test
    void execute_validMessage_returnsSuccess() {
        String roomId = "meeting-123";
        ChatRoom room = new ChatRoom();
        room.setStatus(ChatRoom.RoomStatus.ACTIVE);

        ChatMessage saved = ChatMessage.send(1L, roomId, "user-1", "Alice", "Hello!", null);

        when(chatRoomRepository.findByRoomId(roomId)).thenReturn(java.util.Optional.of(room));
        when(chatLimitsPort.getMaxMessageLength()).thenReturn(4000);
        when(seqCounter.nextSeq(roomId)).thenReturn(1L);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(saved);

        Result<ChatMessage, ChatError> result =
                useCase.execute(roomId, "user-1", "Alice", "Hello!", null);

        assertThat(result).isInstanceOf(Result.Success.class);
        verify(chatMessageRepository).save(any(ChatMessage.class));
        verify(chatLiveKitPort).broadcastMessage(roomId, saved);
    }

    @Test
    void execute_roomNotFound_returnsRoomNotFoundError() {
        String roomId = "nonexistent";
        when(chatRoomRepository.findByRoomId(roomId)).thenReturn(java.util.Optional.empty());

        Result<ChatMessage, ChatError> result =
                useCase.execute(roomId, "user-1", "Alice", "Hello!", null);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<ChatMessage, ChatError>) result).error())
                .isInstanceOf(ChatError.RoomNotFound.class);
    }

    @Test
    void execute_roomNotActive_returnsUnauthorizedError() {
        String roomId = "meeting-123";
        ChatRoom room = new ChatRoom();
        room.setStatus(ChatRoom.RoomStatus.ARCHIVED);

        when(chatRoomRepository.findByRoomId(roomId)).thenReturn(java.util.Optional.of(room));

        Result<ChatMessage, ChatError> result =
                useCase.execute(roomId, "user-1", "Alice", "Hello!", null);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<ChatMessage, ChatError>) result).error())
                .isInstanceOf(ChatError.Unauthorized.class);
    }

    @Test
    void execute_contentTooLong_returnsMessageTooLongError() {
        String roomId = "meeting-123";
        ChatRoom room = new ChatRoom();
        room.setStatus(ChatRoom.RoomStatus.ACTIVE);

        when(chatRoomRepository.findByRoomId(roomId)).thenReturn(java.util.Optional.of(room));
        when(chatLimitsPort.getMaxMessageLength()).thenReturn(4000);

        String tooLongContent = "a".repeat(4001);

        Result<ChatMessage, ChatError> result =
                useCase.execute(roomId, "user-1", "Alice", tooLongContent, null);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<ChatMessage, ChatError>) result).error())
                .isInstanceOf(ChatError.MessageTooLong.class);
    }

    @Test
    void execute_messageSavedEvenIfLiveKitBroadcastFails() {
        String roomId = "meeting-123";
        ChatRoom room = new ChatRoom();
        room.setStatus(ChatRoom.RoomStatus.ACTIVE);

        ChatMessage saved = ChatMessage.send(1L, roomId, "user-1", "Alice", "Hello!", null);

        when(chatRoomRepository.findByRoomId(roomId)).thenReturn(java.util.Optional.of(room));
        when(chatLimitsPort.getMaxMessageLength()).thenReturn(4000);
        when(seqCounter.nextSeq(roomId)).thenReturn(1L);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(saved);
        when(chatLiveKitPort.broadcastMessage(roomId, saved))
                .thenReturn(
                        Result.failure(new ChatError.PersistenceFailure("LiveKit unavailable")));

        Result<ChatMessage, ChatError> result =
                useCase.execute(roomId, "user-1", "Alice", "Hello!", null);

        assertThat(result).isInstanceOf(Result.Success.class);
        verify(chatMessageRepository).save(any(ChatMessage.class));
    }

    @Test
    void execute_contentExceedsConfigurableMax_usesConfiguredLimit() {
        String roomId = "meeting-123";
        ChatRoom room = new ChatRoom();
        room.setStatus(ChatRoom.RoomStatus.ACTIVE);

        when(chatRoomRepository.findByRoomId(roomId)).thenReturn(java.util.Optional.of(room));
        when(chatLimitsPort.getMaxMessageLength()).thenReturn(100); // custom limit

        String tooLongContent = "a".repeat(101);

        Result<ChatMessage, ChatError> result =
                useCase.execute(roomId, "user-1", "Alice", tooLongContent, null);

        assertThat(result).isInstanceOf(Result.Failure.class);
        ChatError.MessageTooLong error = (ChatError.MessageTooLong)
                ((Result.Failure<ChatMessage, ChatError>) result).error();
        assertThat(error.maxLength()).isEqualTo(100);
        assertThat(error.actualLength()).isEqualTo(101);
    }
}
