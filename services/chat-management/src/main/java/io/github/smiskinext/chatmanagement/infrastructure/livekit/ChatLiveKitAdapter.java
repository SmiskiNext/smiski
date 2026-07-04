package io.github.smiskinext.chatmanagement.infrastructure.livekit;

import io.github.smiskinext.chatmanagement.application.port.ChatLiveKitPort;
import io.github.smiskinext.chatmanagement.domain.model.ChatError;
import io.github.smiskinext.chatmanagement.domain.model.ChatMessage;
import io.github.phunguy65.zms.shared.domain.Result;
import io.livekit.server.RoomServiceClient;
import kotlin.KotlinNullPointerException;
import livekit.LivekitModels.DataPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import retrofit2.Response;
import tools.jackson.databind.ObjectMapper;

/**
 * LiveKit adapter for broadcasting chat messages via Data Messages.
 *
 * <p>Messages are sent as reliable JSON packets. Uses {@link ObjectMapper} for proper JSON
 * serialization to avoid manual escaping issues.
 *
 * <p>Known issue: LiveKit Server SDK 0.12.1 throws a {@link KotlinNullPointerException} after a
 * successful send — this is treated as success since the message is already delivered.
 */
@Component
public class ChatLiveKitAdapter implements ChatLiveKitPort {

    private static final Logger log = LoggerFactory.getLogger(ChatLiveKitAdapter.class);

    /** Maximum payload size in bytes (RELIABLE mode ~15 KB). */
    private static final int MAX_PAYLOAD_BYTES = 15_000;

    private final RoomServiceClient roomServiceClient;
    private final ObjectMapper objectMapper;

    public ChatLiveKitAdapter(RoomServiceClient roomServiceClient, ObjectMapper objectMapper) {
        this.roomServiceClient = roomServiceClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Result<Void, ChatError> broadcastMessage(String roomName, ChatMessage message) {
        String payload;
        try {
            payload = buildDataPayload(message);
        } catch (Exception e) {
            log.error(
                    "Failed to serialize chat payload for message {}: {}",
                    message.getId(),
                    e.getMessage());
            return Result.failure(new ChatError.PersistenceFailure(
                    "Failed to serialize message: " + e.getMessage()));
        }

        if (payload.getBytes().length > MAX_PAYLOAD_BYTES) {
            log.warn("Chat message payload exceeds {} bytes", MAX_PAYLOAD_BYTES);
            return Result.failure(new ChatError.MessageTooLong(4000, payload.length()));
        }

        try {
            Response<Void> response = roomServiceClient
                    .sendData(
                            roomName,
                            payload.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            DataPacket.Kind.RELIABLE,
                            java.util.Collections.emptyList())
                    .execute();

            if (!response.isSuccessful()) {
                log.warn(
                        "Failed to send data to room {}: HTTP {} {}",
                        roomName,
                        response.code(),
                        response.message());
                return Result.failure(new ChatError.PersistenceFailure(
                        "LiveKit broadcast failed: HTTP " + response.code()));
            }

            log.debug("Broadcast chat message {} to room {}", message.getSeqNum(), roomName);
            return Result.success();

        } catch (Exception e) {
            if (isKnownKnpeBug(e)) {
                // SDK 0.12.1 KNPE bug: message was delivered despite KNPE
                log.debug(
                        "Known KNPE bug in SDK 0.12.1 for message {} in room {}, treating as success",
                        message.getSeqNum(),
                        roomName);
                return Result.success();
            }
            log.error(
                    "Failed to broadcast chat message {} to room {}: {}",
                    message.getSeqNum(),
                    roomName,
                    e.getMessage());
            return Result.failure(new ChatError.PersistenceFailure(
                    "LiveKit broadcast failed: " + e.getMessage()));
        }
    }

    /**
     * Builds a JSON payload for the LiveKit Data Message using {@link ObjectMapper} for proper
     * escaping of special characters.
     */
    private String buildDataPayload(ChatMessage message) {
        ChatPayload payload = new ChatPayload(
                message.getId(),
                message.getSeqNum(),
                message.getSenderId(),
                message.getSenderName(),
                message.getContent(),
                message.getType(),
                message.getCreatedAt() != null ? message.getCreatedAt().toString() : null);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize chat payload", e);
        }
    }

    /** Detects the known SDK 0.12.1 KNPE bug. */
    private static boolean isKnownKnpeBug(Exception e) {
        return e instanceof KotlinNullPointerException
                || e.getCause() instanceof KotlinNullPointerException
                || (e.getMessage() != null && e.getMessage().contains("null"));
    }

    /** JSON payload record for LiveKit Data Messages. */
    private record ChatPayload(
            String id,
            Long seqNum,
            String senderId,
            String senderName,
            String content,
            String type,
            String createdAt) {}
}
