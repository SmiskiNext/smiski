package io.github.smiskinext.chatmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.smiskinext.chatmanagement.application.port.ChatMessageRepository;
import io.github.smiskinext.chatmanagement.application.port.ChatRoomRepository;
import io.github.smiskinext.chatmanagement.domain.model.ChatMessage;
import io.github.smiskinext.chatmanagement.domain.model.ChatRoom;
import io.github.phunguy65.zms.shared.domain.CursorPageResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetMessagesUseCaseTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    private GetMessagesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetMessagesUseCase(chatRoomRepository, chatMessageRepository);
    }

    @Test
    void execute_roomNotFound_returnsEmptyPage() {
        String roomId = "nonexistent";
        when(chatRoomRepository.findByRoomId(roomId)).thenReturn(Optional.empty());

        CursorPageResponse<ChatMessage> result = useCase.execute(roomId, 20, Optional.empty());

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void execute_returnsPaginatedMessages() {
        String roomId = "meeting-123";
        ChatRoom room = new ChatRoom();
        room.setStatus(ChatRoom.RoomStatus.ACTIVE);

        ChatMessage msg1 = ChatMessage.send(2L, roomId, "user-1", "Alice", "Second", null);
        ChatMessage msg2 = ChatMessage.send(1L, roomId, "user-2", "Bob", "First", null);

        CursorPageResponse<ChatMessage> page =
                CursorPageResponse.of(List.of(msg1, msg2), 20, false);

        when(chatRoomRepository.findByRoomId(roomId)).thenReturn(Optional.of(room));
        when(chatMessageRepository.findByRoomId(roomId, 20, Optional.empty())).thenReturn(page);

        CursorPageResponse<ChatMessage> result = useCase.execute(roomId, 20, Optional.empty());

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().getFirst().getSeqNum()).isEqualTo(2L);
        assertThat(result.hasNext()).isFalse();
    }
}
