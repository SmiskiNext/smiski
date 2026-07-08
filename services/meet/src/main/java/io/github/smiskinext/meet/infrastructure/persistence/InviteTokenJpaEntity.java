package io.github.smiskinext.meet.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * JPA entity for the {@code invite_tokens} table.
 *
 * <p>The {@code token_hash} column holds the SHA-256 hash of the raw invite token string.
 * Raw token values are never stored in the database.
 */
@Entity
@Table(name = "invite_tokens")
public class InviteTokenJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 255, updatable = false)
    private String tenantId;

    @Column(name = "meeting_id", nullable = false, columnDefinition = "uuid")
    private UUID meetingId;

    @Column(name = "invitee_id", nullable = false, columnDefinition = "uuid")
    private UUID inviteeId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InviteTokenJpaEntity() {}

    public InviteTokenJpaEntity(
            UUID id,
            UUID meetingId,
            UUID inviteeId,
            String tokenHash,
            String status,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.meetingId = meetingId;
        this.inviteeId = inviteeId;
        this.tokenHash = tokenHash;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public UUID getInviteeId() {
        return inviteeId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
