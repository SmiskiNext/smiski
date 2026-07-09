package io.github.smiskinext.meet.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "participation_logs")
public class ParticipationLogJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 255, updatable = false)
    private String tenantId;

    @Column(name = "meeting_id", nullable = false, columnDefinition = "uuid")
    private UUID meetingId;

    @Column(name = "account_id", nullable = false, length = 128)
    private String accountId;

    @Column(name = "display_name", length = 255)
    private @Nullable String displayName;

    @Column(name = "display_name_cached_at")
    private @Nullable Instant displayNameCachedAt;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "livekit_identity", nullable = false, length = 255)
    private String livekitIdentity;

    @Column(name = "livekit_participant_sid", length = 50)
    private @Nullable String livekitParticipantSid;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private @Nullable Instant leftAt;

    protected ParticipationLogJpaEntity() {}

    public ParticipationLogJpaEntity(
            UUID id,
            UUID meetingId,
            String accountId,
            @Nullable String displayName,
            String role,
            String livekitIdentity,
            @Nullable String livekitParticipantSid,
            Instant joinedAt,
            @Nullable Instant leftAt,
            @Nullable Instant displayNameCachedAt) {
        this.id = id;
        this.meetingId = meetingId;
        this.accountId = accountId;
        this.displayName = displayName;
        this.role = role;
        this.livekitIdentity = livekitIdentity;
        this.livekitParticipantSid = livekitParticipantSid;
        this.joinedAt = joinedAt;
        this.leftAt = leftAt;
        this.displayNameCachedAt = displayNameCachedAt;
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

    public String getAccountId() {
        return accountId;
    }

    public @Nullable String getDisplayName() {
        return displayName;
    }

    public String getRole() {
        return role;
    }

    public String getLivekitIdentity() {
        return livekitIdentity;
    }

    public @Nullable String getLivekitParticipantSid() {
        return livekitParticipantSid;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public @Nullable Instant getLeftAt() {
        return leftAt;
    }

    public @Nullable Instant getDisplayNameCachedAt() {
        return displayNameCachedAt;
    }

    public void setLeftAt(Instant leftAt) {
        this.leftAt = leftAt;
    }

    public void setLivekitParticipantSid(String sid) {
        this.livekitParticipantSid = sid;
    }
}
