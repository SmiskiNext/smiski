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

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "rsvp", nullable = false)
    private boolean rsvp;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt;

    @Column(name = "responded_at")
    private @Nullable Instant respondedAt;

    @Column(name = "removed_at")
    private @Nullable Instant removedAt;

    protected MeetingInviteeJpaEntity() {}

    public MeetingInviteeJpaEntity(
            UUID id,
            UUID meetingId,
            String inviterId,
            String accountId,
            String email,
            String displayName,
            String role,
            boolean rsvp,
            String status,
            Instant invitedAt,
            @Nullable Instant respondedAt,
            @Nullable Instant removedAt) {
        this.id = id;
        this.meetingId = meetingId;
        this.inviterId = inviterId;
        this.accountId = accountId;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.rsvp = rsvp;
        this.status = status;
        this.invitedAt = invitedAt;
        this.respondedAt = respondedAt;
        this.removedAt = removedAt;
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

    public String getRole() {
        return role;
    }

    public boolean isRsvp() {
        return rsvp;
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

    public @Nullable Instant getRemovedAt() {
        return removedAt;
    }
}
