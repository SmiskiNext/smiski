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
@Table(name = "meeting_invitees")
public class MeetingInviteeJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 255, updatable = false)
    private String tenantId;

    @Column(name = "meeting_id", nullable = false, columnDefinition = "uuid")
    private UUID meetingId;

    @Column(name = "inviter_id", nullable = false, length = 128)
    private String inviterId;

    @Column(name = "account_id", nullable = false, length = 128)
    private String accountId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt;

    @Column(name = "responded_at")
    private @Nullable Instant respondedAt;

    @Column(name = "token_hash", length = 64)
    private @Nullable String tokenHash;

    @Column(name = "token_status", length = 20)
    private @Nullable String tokenStatus;

    @Column(name = "token_expires_at")
    private @Nullable Instant tokenExpiresAt;

    @Column(name = "token_created_at")
    private @Nullable Instant tokenCreatedAt;

    @Column(name = "token_updated_at")
    private @Nullable Instant tokenUpdatedAt;

    protected MeetingInviteeJpaEntity() {}

    public MeetingInviteeJpaEntity(
            UUID id,
            UUID meetingId,
            String inviterId,
            String accountId,
            String email,
            String displayName,
            String status,
            Instant invitedAt,
            @Nullable Instant respondedAt,
            @Nullable String tokenHash,
            @Nullable String tokenStatus,
            @Nullable Instant tokenExpiresAt,
            @Nullable Instant tokenCreatedAt,
            @Nullable Instant tokenUpdatedAt) {
        this.id = id;
        this.meetingId = meetingId;
        this.inviterId = inviterId;
        this.accountId = accountId;
        this.email = email;
        this.displayName = displayName;
        this.status = status;
        this.invitedAt = invitedAt;
        this.respondedAt = respondedAt;
        this.tokenHash = tokenHash;
        this.tokenStatus = tokenStatus;
        this.tokenExpiresAt = tokenExpiresAt;
        this.tokenCreatedAt = tokenCreatedAt;
        this.tokenUpdatedAt = tokenUpdatedAt;
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

    public String getInviterId() {
        return inviterId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getStatus() {
        return status;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public @Nullable Instant getRespondedAt() {
        return respondedAt;
    }

    public @Nullable String getTokenHash() {
        return tokenHash;
    }

    public @Nullable String getTokenStatus() {
        return tokenStatus;
    }

    public @Nullable Instant getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public @Nullable Instant getTokenCreatedAt() {
        return tokenCreatedAt;
    }

    public @Nullable Instant getTokenUpdatedAt() {
        return tokenUpdatedAt;
    }
}
