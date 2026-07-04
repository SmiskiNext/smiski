package io.github.smiskinext.chatmanagement.presentation.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.smiskinext.chatmanagement.application.usecase.GetMessagesUseCase;
import io.github.smiskinext.chatmanagement.application.usecase.GetRoomUseCase;
import io.github.smiskinext.chatmanagement.application.usecase.SendMessageUseCase;
import io.github.smiskinext.chatmanagement.domain.model.ChatError;
import io.github.smiskinext.chatmanagement.domain.model.ChatMessage;
import io.github.smiskinext.chatmanagement.domain.model.ChatRoom;
import io.github.phunguy65.zms.shared.domain.CursorPageResponse;
import io.github.phunguy65.zms.shared.domain.Result;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;

/**
 * Unit tests for ChatController REST endpoints.
 *
 * <p>Uses standalone MockMvc setup (no Spring context) to test HTTP behavior in isolation.
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private SendMessageUseCase sendMessageUseCase;

    @Mock
    private GetMessagesUseCase getMessagesUseCase;

    @Mock
    private GetRoomUseCase getRoomUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ChatController controller =
                new ChatController(sendMessageUseCase, getMessagesUseCase, getRoomUseCase);
        var versionStrategy = new DefaultApiVersionStrategy(
                List.of(new PathApiVersionResolver(0)),
                new SemanticApiVersionParser(),
                null,
                "1.0",
                true,
                null,
                null);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setApiVersionStrategy(versionStrategy)
                .build();
        // Default: authenticated user
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken("user-123", null, List.of()));
    }

    // ─── POST /{roomId}/messages ──────────────────────────────────────────────

    @Test
    void sendMessage_validRequest_returns200WithMessage() throws Exception {
        String roomId = "room-1";
        ChatMessage message = ChatMessage.send(1L, roomId, "user-123", "Alice", "Hello!", null);

        when(sendMessageUseCase.execute(eq(roomId), anyString(), anyString(), anyString(), any()))
                .thenReturn(Result.success(message));

        mockMvc.perform(post("/1.0/chat/rooms/{roomId}/messages", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"senderName": "Alice", "content": "Hello!", "replyToSeqNum": null}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seqNum").value(1))
                .andExpect(jsonPath("$.data.content").value("Hello!"));
    }

    @Test
    void sendMessage_roomNotFound_returns404() throws Exception {
        String roomId = "nonexistent";
        when(sendMessageUseCase.execute(eq(roomId), anyString(), anyString(), anyString(), any()))
                .thenReturn(Result.failure(new ChatError.RoomNotFound(roomId)));

        mockMvc.perform(post("/1.0/chat/rooms/{roomId}/messages", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"senderName": "Alice", "content": "Hello!", "replyToSeqNum": null}
                            """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.data.message")
                        .value(org.hamcrest.Matchers.containsString("Room not found")));
    }

    @Test
    void sendMessage_roomInactive_returns403() throws Exception {
        String roomId = "room-1";
        when(sendMessageUseCase.execute(eq(roomId), anyString(), anyString(), anyString(), any()))
                .thenReturn(Result.failure(new ChatError.Unauthorized("Meeting has ended")));

        mockMvc.perform(post("/1.0/chat/rooms/{roomId}/messages", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"senderName": "Alice", "content": "Hello!", "replyToSeqNum": null}
                            """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.data.message").value("Unauthorized: Meeting has ended"));
    }

    @Test
    void sendMessage_messageTooLong_returns400() throws Exception {
        String roomId = "room-1";
        when(sendMessageUseCase.execute(eq(roomId), anyString(), anyString(), anyString(), any()))
                .thenReturn(Result.failure(new ChatError.MessageTooLong(100, 150)));

        mockMvc.perform(post("/1.0/chat/rooms/{roomId}/messages", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"senderName": "Alice", "content": "too long", "replyToSeqNum": null}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("MESSAGE_TOO_LONG"))
                .andExpect(jsonPath("$.data.message")
                        .value(org.hamcrest.Matchers.containsString("exceeds maximum length")));
    }

    @Test
    void sendMessage_persistenceFailure_returns500ErrorEnvelope() throws Exception {
        String roomId = "room-1";
        when(sendMessageUseCase.execute(eq(roomId), anyString(), anyString(), anyString(), any()))
                .thenReturn(
                        Result.failure(new ChatError.PersistenceFailure("MongoDB write failed")));

        mockMvc.perform(post("/1.0/chat/rooms/{roomId}/messages", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"senderName": "Alice", "content": "Hello!", "replyToSeqNum": null}
                            """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("Persistence failure")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void sendMessage_unauthenticated_returns401() throws Exception {
        String roomId = "room-1";
        SecurityContextHolder.clearContext();

        mockMvc.perform(post("/1.0/chat/rooms/{roomId}/messages", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"senderName": "Alice", "content": "Hello!", "replyToSeqNum": null}
                            """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    // ─── GET /{roomId}/messages ───────────────────────────────────────────────

    @Test
    void getMessages_returnsPaginatedMessages() throws Exception {
        String roomId = "room-1";
        ChatMessage msg = ChatMessage.send(1L, roomId, "user-1", "Alice", "Hello!", null);
        CursorPageResponse<ChatMessage> page = CursorPageResponse.of(List.of(msg), 20, false);

        when(getMessagesUseCase.execute(eq(roomId), anyInt(), any())).thenReturn(page);

        mockMvc.perform(get("/1.0/chat/rooms/{roomId}/messages", roomId).param("size", "20"))
                .andExpect(status().isOk())
                // CursorScrollResponse uses "content" not "items", "nextPageToken" not "hasNext"
                .andExpect(jsonPath("$.data.content[0].seqNum").value(1))
                .andExpect(jsonPath("$.data.content[0].content").value("Hello!"))
                // nextPageToken is null when hasNext=false — field absent from JSON
                .andExpect(jsonPath("$.data.nextPageToken").doesNotExist());
    }

    @Test
    void getMessages_withBeforeSeqNum_passesCursorToUseCase() throws Exception {
        String roomId = "room-1";
        CursorPageResponse<ChatMessage> page = CursorPageResponse.of(List.of(), 20, false);

        when(getMessagesUseCase.execute(eq(roomId), anyInt(), eq(Optional.of(5L))))
                .thenReturn(page);

        mockMvc.perform(get("/1.0/chat/rooms/{roomId}/messages", roomId)
                        .param("size", "20")
                        .param("beforeSeqNum", "5"))
                .andExpect(status().isOk());

        verify(getMessagesUseCase).execute(roomId, 20, Optional.of(5L));
    }

    @Test
    void getMessages_sizeTooLarge_clampedTo100() throws Exception {
        String roomId = "room-1";
        CursorPageResponse<ChatMessage> page = CursorPageResponse.of(List.of(), 100, false);

        when(getMessagesUseCase.execute(eq(roomId), eq(100), any())).thenReturn(page);

        mockMvc.perform(get("/1.0/chat/rooms/{roomId}/messages", roomId).param("size", "500"))
                .andExpect(status().isOk());

        // Controller uses Math.clamp(size, 1, MAX_SIZE=100)
        verify(getMessagesUseCase).execute(roomId, 100, Optional.empty());
    }

    @Test
    void getMessages_sizeTooSmall_clampedTo1() throws Exception {
        String roomId = "room-1";
        CursorPageResponse<ChatMessage> page = CursorPageResponse.of(List.of(), 1, false);

        when(getMessagesUseCase.execute(eq(roomId), eq(1), any())).thenReturn(page);

        mockMvc.perform(get("/1.0/chat/rooms/{roomId}/messages", roomId).param("size", "0"))
                .andExpect(status().isOk());

        verify(getMessagesUseCase).execute(roomId, 1, Optional.empty());
    }

    @Test
    void getMessages_negativeSize_clampedTo1() throws Exception {
        String roomId = "room-1";
        CursorPageResponse<ChatMessage> page = CursorPageResponse.of(List.of(), 1, false);

        when(getMessagesUseCase.execute(eq(roomId), eq(1), any())).thenReturn(page);

        mockMvc.perform(get("/1.0/chat/rooms/{roomId}/messages", roomId).param("size", "-5"))
                .andExpect(status().isOk());

        verify(getMessagesUseCase).execute(roomId, 1, Optional.empty());
    }

    @Test
    void getMessages_hasNext_true_providesNextPageToken() throws Exception {
        String roomId = "room-1";
        ChatMessage msg1 = ChatMessage.send(10L, roomId, "user-1", "Alice", "First", null);
        ChatMessage msg2 = ChatMessage.send(9L, roomId, "user-1", "Bob", "Second", null);
        // hasNext=true: last item's seqNum = 9 → next page token
        CursorPageResponse<ChatMessage> page = CursorPageResponse.of(List.of(msg1, msg2), 20, true);

        when(getMessagesUseCase.execute(eq(roomId), anyInt(), any())).thenReturn(page);

        mockMvc.perform(get("/1.0/chat/rooms/{roomId}/messages", roomId).param("size", "20"))
                .andExpect(status().isOk())
                // nextPageToken = String.valueOf(last item's seqNum = 9)
                .andExpect(jsonPath("$.data.nextPageToken").value("9"))
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    // ─── GET /{roomId} ───────────────────────────────────────────────────────

    @Test
    void getRoom_roomExists_returns200WithRoom() throws Exception {
        String roomId = "room-1";
        ChatRoom room = ChatRoom.create(roomId);

        when(getRoomUseCase.execute(roomId)).thenReturn(Result.success(room));

        mockMvc.perform(get("/1.0/chat/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomId").value(roomId));
    }

    @Test
    void getRoom_roomNotFound_returns404() throws Exception {
        String roomId = "nonexistent";
        when(getRoomUseCase.execute(roomId))
                .thenReturn(Result.failure(new ChatError.RoomNotFound(roomId)));

        mockMvc.perform(get("/1.0/chat/rooms/{roomId}", roomId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.data.message")
                        .value(org.hamcrest.Matchers.containsString("Room not found")));
    }

    @Test
    void getRoom_persistenceFailure_returns500ErrorEnvelope() throws Exception {
        String roomId = "room-1";
        when(getRoomUseCase.execute(roomId))
                .thenReturn(
                        Result.failure(new ChatError.PersistenceFailure("MongoDB read failed")));

        mockMvc.perform(get("/1.0/chat/rooms/{roomId}", roomId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("Persistence failure")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
