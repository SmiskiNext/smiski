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

    @Column(name = "account_id", length = 128)
    private @Nullable String accountId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "display_name", length = 255)
    private @Nullable String displayName;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt;

    @Column(name = "responded_at")
    private @Nullable Instant respondedAt;

    @Column(name = "invite_token_id", columnDefinition = "uuid")
    private @Nullable UUID inviteTokenId;

    protected MeetingInviteeJpaEntity() {}

    public MeetingInviteeJpaEntity(
            UUID id,
            UUID meetingId,
            String inviterId,
            @Nullable String accountId,
            String email,
            @Nullable String displayName,
            String status,
            Instant invitedAt,
            @Nullable Instant respondedAt,
            @Nullable UUID inviteTokenId) {
        this.id = id;
        this.meetingId = meetingId;
        this.inviterId = inviterId;
        this.accountId = accountId;
        this.email = email;
        this.displayName = displayName;
        this.status = status;
        this.invitedAt = invitedAt;
        this.respondedAt = respondedAt;
        this.inviteTokenId = inviteTokenId;
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

    public @Nullable String getAccountId() {
        return accountId;
    }

    public String getEmail() {
        return email;
    }

    public @Nullable String getDisplayName() {
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

    public @Nullable UUID getInviteTokenId() {
        return inviteTokenId;
    }
}
