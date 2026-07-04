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
 * Represents a chat room that groups messages for a specific meeting.
 *
 * <p>The room is created when a meeting starts. It tracks the lifecycle status of the chat
 * (ACTIVE when the meeting is live, ARCHIVED when the meeting ends).
 */
@Document(collection = "chat_rooms")
@CompoundIndexes({@CompoundIndex(name = "idx_meeting_id", def = "{'meetingId': 1}")})
public class ChatRoom {

    @Id
    private String id;

    /** Unique room identifier (also the meeting ID). */
    @Indexed(unique = true)
    private String roomId;

    /** The meeting this chat room belongs to. */
    private String meetingId;

    /** Room status: ACTIVE, ARCHIVED, or DELETED. */
    private String status;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public ChatRoom() {}

    private ChatRoom(
            String roomId, String meetingId, String status, Instant createdAt, Instant updatedAt) {
        this.roomId = roomId;
        this.meetingId = meetingId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ChatRoom create(String meetingId) {
        return new ChatRoom(meetingId, meetingId, RoomStatus.ACTIVE, Instant.now(), Instant.now());
    }

    public String getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getMeetingId() {
        return meetingId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** Room status constants. */
    public static final class RoomStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String ARCHIVED = "ARCHIVED";
        public static final String DELETED = "DELETED";
    }
}
