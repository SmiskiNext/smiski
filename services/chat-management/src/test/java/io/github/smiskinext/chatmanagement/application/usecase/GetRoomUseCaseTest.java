package io.github.smiskinext.chatmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.smiskinext.chatmanagement.application.port.ChatRoomRepository;
import io.github.smiskinext.chatmanagement.domain.model.ChatError;
import io.github.smiskinext.chatmanagement.domain.model.ChatRoom;
import io.github.phunguy65.zms.shared.domain.Result;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetRoomUseCaseTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    private GetRoomUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetRoomUseCase(chatRoomRepository);
    }

    @Test
    void execute_roomFound_returnsSuccess() {
        String roomId = "meeting-123";
        ChatRoom room = ChatRoom.create(roomId);

        when(chatRoomRepository.findByRoomId(roomId)).thenReturn(Optional.of(room));

        Result<ChatRoom, ChatError> result = useCase.execute(roomId);

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(((Result.Success<ChatRoom, ChatError>) result).value().getRoomId())
                .isEqualTo(roomId);
    }

    @Test
    void execute_roomNotFound_returnsRoomNotFoundError() {
        String roomId = "nonexistent";

        when(chatRoomRepository.findByRoomId(roomId)).thenReturn(Optional.empty());

        Result<ChatRoom, ChatError> result = useCase.execute(roomId);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<ChatRoom, ChatError>) result).error())
                .isInstanceOf(ChatError.RoomNotFound.class);
    }
}
