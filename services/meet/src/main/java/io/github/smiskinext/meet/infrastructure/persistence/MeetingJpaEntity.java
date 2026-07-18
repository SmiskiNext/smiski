package io.github.smiskinext.meet.infrastructure.persistence;

import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "meetings")
public class MeetingJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 255, updatable = false)
    private String tenantId;

    @Column(name = "host_id", nullable = false, length = 128)
    private String hostId;

    @Column(name = "short_code", nullable = false, length = 15, unique = true)
    private String shortCode;

    @Column(length = 255, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "issue_id", length = 64, nullable = false)
    private String issueId;

    @Column(name = "issue_key", length = 64, nullable = false)
    private String issueKey;

    @Column(name = "project_key", length = 64, nullable = false)
    private String projectKey;

    @Column(name = "start_time")
    private @Nullable Instant startTime;

    @Column(name = "end_time")
    private @Nullable Instant endTime;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "cancel_reason", length = 20)
    private @Nullable String cancelReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private MeetingSettings settings;

    @Column(name = "deleted_at")
    private @Nullable Instant deletedAt;

    @Column(name = "deleted_by", length = 128)
    private @Nullable String deletedBy;

    @Column(name = "purge_after")
    private @Nullable Instant purgeAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "zone_id", nullable = false, length = 64)
    private String zoneId;

    protected MeetingJpaEntity() {}

    public MeetingJpaEntity(
            UUID id,
            String hostId,
            String shortCode,
            String title,
            String description,
            String issueId,
            String issueKey,
            String projectKey,
            @Nullable Instant startTime,
            @Nullable Instant endTime,
            String type,
            String status,
            @Nullable String cancelReason,
            MeetingSettings settings,
            @Nullable Instant deletedAt,
            @Nullable String deletedBy,
            @Nullable Instant purgeAfter,
            Instant createdAt,
            String zoneId) {
        this.id = id;
        this.hostId = hostId;
        this.shortCode = shortCode;
        this.title = title;
        this.description = description;
        this.issueId = issueId;
        this.issueKey = issueKey;
        this.projectKey = projectKey;
        this.startTime = startTime;
        this.endTime = endTime;
        this.type = type;
        this.status = status;
        this.cancelReason = cancelReason;
        this.settings = settings;
        this.deletedAt = deletedAt;
        this.deletedBy = deletedBy;
        this.purgeAfter = purgeAfter;
        this.createdAt = createdAt;
        this.zoneId = zoneId;
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getHostId() {
        return hostId;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getIssueId() {
        return issueId;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public @Nullable Instant getStartTime() {
        return startTime;
    }

    public @Nullable Instant getEndTime() {
        return endTime;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public @Nullable String getCancelReason() {
        return cancelReason;
    }

    public MeetingSettings getSettings() {
        return settings;
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

    public String getZoneId() {
        return zoneId;
    }
}
