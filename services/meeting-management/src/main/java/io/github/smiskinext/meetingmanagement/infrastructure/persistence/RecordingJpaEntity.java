package io.github.smiskinext.meetingmanagement.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "recordings")
public class RecordingJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "meeting_id", nullable = false, columnDefinition = "uuid")
    private UUID meetingId;

    @Column(name = "livekit_egress_id", length = 50, unique = true)
    private @Nullable String livekitEgressId;

    @Column(name = "livekit_room_name", length = 255)
    private @Nullable String livekitRoomName;

    @Column(name = "file_url", length = 2048)
    private @Nullable String fileUrl;

    @Column(name = "thumbnail_url", length = 2048)
    private @Nullable String thumbnailUrl;

    @Column(name = "storage_path", length = 2048)
    private @Nullable String storagePath;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private @Nullable Instant endedAt;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "error_message", length = 1024)
    private @Nullable String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RecordingJpaEntity() {}

    public RecordingJpaEntity(
            UUID id,
            UUID meetingId,
            @Nullable String livekitEgressId,
            @Nullable String livekitRoomName,
            @Nullable String fileUrl,
            @Nullable String thumbnailUrl,
            @Nullable String storagePath,
            String status,
            Instant startedAt,
            @Nullable Instant endedAt,
            int durationSeconds,
            long fileSizeBytes,
            @Nullable String errorMessage,
            Instant createdAt) {
        this.id = id;
        this.meetingId = meetingId;
        this.livekitEgressId = livekitEgressId;
        this.livekitRoomName = livekitRoomName;
        this.fileUrl = fileUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.storagePath = storagePath;
        this.status = status;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationSeconds = durationSeconds;
        this.fileSizeBytes = fileSizeBytes;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMeetingId() {
        return meetingId;
    }

    public @Nullable String getLivekitEgressId() {
        return livekitEgressId;
    }

    public @Nullable String getLivekitRoomName() {
        return livekitRoomName;
    }

    public @Nullable String getFileUrl() {
        return fileUrl;
    }

    public @Nullable String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public @Nullable String getStoragePath() {
        return storagePath;
    }

    public String getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public @Nullable Instant getEndedAt() {
        return endedAt;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public @Nullable String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
