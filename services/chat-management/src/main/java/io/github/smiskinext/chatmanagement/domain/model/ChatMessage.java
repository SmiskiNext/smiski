package io.github.smiskinext.chatmanagement.domain.model;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Represents a single chat message within a room.
 *
 * <p>Messages are ordered by a Snowflake-generated sequence number ({@code seqNum})
 * that guarantees monotonic ordering within a room across all service instances.
 * A TTL index on {@code createdAt} automatically removes messages older than 30 days.
 */
@Document(collection = "chat_messages")
@CompoundIndexes({
    @CompoundIndex(name = "idx_room_seqnum", def = "{'roomId': 1, 'seqNum': 1}"),
    @CompoundIndex(name = "idx_room_created_at", def = "{'roomId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "idx_sender_created_at", def = "{'senderId': 1, 'createdAt': -1}")
})
public class ChatMessage {

    @Id
    private String id;

    /**
     * Snowflake-generated sequence number. Monotonically increasing within a room.
     * Used as the primary ordering key and cursor for pagination.
     */
    private Long seqNum;

    /** The room this message belongs to. */
    @Indexed
    private String roomId;

    /** The user who sent this message. */
    private String senderId;

    /** Display name of the sender, stored denormalized for history queries. */
    private String senderName;

    /** Message body content. */
    private String content;

    /** Message type: TEXT or SYSTEM. */
    private String type;

    /** Optional metadata (reply reference, edit timestamp). */
    private MessageMetadata metadata;

    @CreatedDate
    @Indexed(expireAfter = "30d")
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /** Whether this message has been soft-deleted. */
    private boolean deleted;

    private Instant deletedAt;

    private String deletedBy;

    public ChatMessage() {}

    private ChatMessage(
            Long seqNum,
            String roomId,
            String senderId,
            String senderName,
            String content,
            String type,
            MessageMetadata metadata,
            Instant createdAt,
            Instant updatedAt,
            boolean deleted,
            Instant deletedAt,
            String deletedBy) {
        this.seqNum = seqNum;
        this.roomId = roomId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.type = type;
        this.metadata = metadata;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deleted = deleted;
        this.deletedAt = deletedAt;
        this.deletedBy = deletedBy;
    }

    /** Creates a user-sent TEXT message. */
    public static ChatMessage send(
            Long seqNum,
            String roomId,
            String senderId,
            String senderName,
            String content,
            Long replyToSeqNum) {
        MessageMetadata meta =
                replyToSeqNum != null ? new MessageMetadata(replyToSeqNum, null) : null;
        return new ChatMessage(
                seqNum,
                roomId,
                senderId,
                senderName,
                content,
                MessageType.TEXT,
                meta,
                Instant.now(),
                Instant.now(),
                false,
                null,
                null);
    }

    /** Creates a system-generated message (join/leave events). */
    public static ChatMessage systemMessage(Long seqNum, String roomId, String content) {
        return new ChatMessage(
                seqNum,
                roomId,
                null,
                "System",
                content,
                MessageType.SYSTEM,
                null,
                Instant.now(),
                Instant.now(),
                false,
                null,
                null);
    }

    public String getId() {
        return id;
    }

    public Long getSeqNum() {
        return seqNum;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getContent() {
        return content;
    }

    public String getType() {
        return type;
    }

    public MessageMetadata getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setSeqNum(Long seqNum) {
        this.seqNum = seqNum;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setMetadata(MessageMetadata metadata) {
        this.metadata = metadata;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    /** Message type constants. */
    public static final class MessageType {
        public static final String TEXT = "TEXT";
        public static final String SYSTEM = "SYSTEM";
    }
}
