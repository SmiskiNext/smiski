package io.github.smiskinext.chatmanagement.application.port;

import io.github.smiskinext.chatmanagement.domain.model.ChatError;
import io.github.smiskinext.chatmanagement.domain.model.ChatMessage;
import io.github.phunguy65.zms.shared.domain.Result;

/** Outbound port: broadcasting a chat message to all LiveKit room participants. */
public interface ChatLiveKitPort {

    /**
     * Broadcasts a chat message payload to all participants in the given LiveKit room.
     *
     * @param roomName the LiveKit room name (e.g. {@code "meeting-{uuid}"})
     * @param message  the chat message to broadcast
     * @return {@code success()} if the broadcast was accepted; {@code failure()} on error.
     *     Note: due to SDK 0.12.1 KNPE bug, a KNPE is treated as success.
     */
    Result<Void, ChatError> broadcastMessage(String roomName, ChatMessage message);
}
