package io.github.smiskinext.record.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "recordings")
public class RecordingJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 255, updatable = false)
    private String tenantId;

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

    @Column(name = "deleted_at")
    private @Nullable Instant deletedAt;

    @Column(name = "deleted_by", length = 128)
    private @Nullable String deletedBy;

    @Column(name = "purge_after")
    private @Nullable Instant purgeAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(length = 255)
    private @Nullable String title;

    @Column(columnDefinition = "text")
    private @Nullable String notes;

    @Column(name = "edited_by", length = 128)
    private @Nullable String editedBy;

    @Column(name = "edited_at")
    private @Nullable Instant editedAt;

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
            @Nullable Instant deletedAt,
            @Nullable String deletedBy,
            @Nullable Instant purgeAfter,
            Instant createdAt,
            @Nullable String title,
            @Nullable String notes,
            @Nullable String editedBy,
            @Nullable Instant editedAt) {
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
        this.deletedAt = deletedAt;
        this.deletedBy = deletedBy;
        this.purgeAfter = purgeAfter;
        this.createdAt = createdAt;
        this.title = title;
        this.notes = notes;
        this.editedBy = editedBy;
        this.editedAt = editedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
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

    public @Nullable Instant getDeletedAt() {
        return deletedAt;
    }

    public @Nullable String getDeletedBy() {
        return deletedBy;
    }

    public @Nullable Instant getPurgeAfter() {
        return purgeAfter;
    }

    public @Nullable String getTitle() {
        return title;
    }

    public @Nullable String getNotes() {
        return notes;
    }

    public @Nullable String getEditedBy() {
        return editedBy;
    }

    public @Nullable Instant getEditedAt() {
        return editedAt;
    }
}
