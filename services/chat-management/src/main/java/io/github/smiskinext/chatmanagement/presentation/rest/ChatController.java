package io.github.smiskinext.chatmanagement.presentation.rest;

import io.github.smiskinext.chatmanagement.application.usecase.GetMessagesUseCase;
import io.github.smiskinext.chatmanagement.application.usecase.GetRoomUseCase;
import io.github.smiskinext.chatmanagement.application.usecase.SendMessageUseCase;
import io.github.smiskinext.chatmanagement.domain.model.ChatError;
import io.github.smiskinext.chatmanagement.domain.model.ChatMessage;
import io.github.smiskinext.chatmanagement.domain.model.ChatRoom;
import io.github.smiskinext.chatmanagement.presentation.rest.request.GetMessagesRequest;
import io.github.smiskinext.chatmanagement.presentation.rest.request.SendMessageRequest;
import io.github.smiskinext.chatmanagement.presentation.rest.response.ChatMessageResponse;
import io.github.smiskinext.chatmanagement.presentation.rest.response.ChatRoomResponse;
import io.github.smiskinext.shared.domain.CursorPageResponse;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.infrastructure.web.CursorScrollResponse;
import io.github.smiskinext.shared.infrastructure.web.JsendResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Chat", description = "Chat room messages and rooms")
public class ChatController extends BaseController {

    private final SendMessageUseCase sendMessageUseCase;
    private final GetMessagesUseCase getMessagesUseCase;
    private final GetRoomUseCase getRoomUseCase;

    public ChatController(
            SendMessageUseCase sendMessageUseCase,
            GetMessagesUseCase getMessagesUseCase,
            GetRoomUseCase getRoomUseCase) {
        this.sendMessageUseCase = sendMessageUseCase;
        this.getMessagesUseCase = getMessagesUseCase;
        this.getRoomUseCase = getRoomUseCase;
    }

    @Operation(summary = "Send a message to a chat room")
    @PostMapping(value = "/{version}/chat/rooms/{roomId}/messages", version = "1.0")
    public ResponseEntity<JsendResponse<ChatMessageResponse>> sendMessage(
            @PathVariable String roomId, @RequestBody SendMessageRequest request) {
        String senderId = extractSenderId();
        if (senderId == null) {
            return unauthenticated();
        }
        Result<ChatMessage, ChatError> result = sendMessageUseCase.execute(
                roomId, senderId, request.senderName(), request.content(), request.replyToSeqNum());

        return switch (result) {
            case Result.Success<ChatMessage, ChatError> s -> {
                ChatMessageResponse response = toMessageResponse(s.value());
                yield ResponseEntity.ok(JsendResponse.success(response));
            }
            case Result.Failure<ChatMessage, ChatError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Get messages from a chat room with cursor pagination")
    @GetMapping(value = "/{version}/chat/rooms/{roomId}/messages", version = "1.0")
    public ResponseEntity<JsendResponse<CursorScrollResponse<ChatMessageResponse>>> getMessages(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long beforeSeqNum) {

        int clampedSize = Math.clamp(size, 1, GetMessagesRequest.MAX_SIZE);
        CursorPageResponse<ChatMessage> page =
                getMessagesUseCase.execute(roomId, clampedSize, Optional.ofNullable(beforeSeqNum));

        List<ChatMessageResponse> items =
                page.items().stream().map(ChatController::toMessageResponse).toList();

        String nextPageToken = null;
        if (page.hasNext() && !items.isEmpty()) {
            nextPageToken = String.valueOf(items.getLast().seqNum());
        }

        var response = new CursorScrollResponse<>(items, page.pageSize(), nextPageToken);
        return ResponseEntity.ok(JsendResponse.success(response));
    }

    @Operation(summary = "Get chat room info")
    @GetMapping(value = "/{version}/chat/rooms/{roomId}", version = "1.0")
    public ResponseEntity<JsendResponse<ChatRoomResponse>> getRoom(@PathVariable String roomId) {
        Result<ChatRoom, ChatError> result = getRoomUseCase.execute(roomId);

        return switch (result) {
            case Result.Success<ChatRoom, ChatError> s -> {
                ChatRoomResponse response = toRoomResponse(s.value());
                yield ResponseEntity.ok(JsendResponse.success(response));
            }
            case Result.Failure<ChatRoom, ChatError> f -> errorResponse(f.error());
        };
    }

    private String extractSenderId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        return auth.getPrincipal().toString();
    }

    private static ChatMessageResponse toMessageResponse(ChatMessage m) {
        return new ChatMessageResponse(
                m.getId(),
                m.getSeqNum(),
                m.getRoomId(),
                m.getSenderId(),
                m.getSenderName(),
                m.getContent(),
                m.getType(),
                m.getCreatedAt());
    }

    private static ChatRoomResponse toRoomResponse(ChatRoom r) {
        return new ChatRoomResponse(
                r.getRoomId(), r.getMeetingId(), r.getStatus(), r.getCreatedAt());
    }
}
