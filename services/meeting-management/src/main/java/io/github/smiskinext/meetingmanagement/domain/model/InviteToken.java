package io.github.smiskinext.meetingmanagement.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteTokenId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteeId;
import io.github.phunguy65.zms.shared.domain.AggregateRoot;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.MeetingId;
import java.time.Instant;

/**
 * Per-invitee cryptographically signed invite token aggregate.
 *
 * <p>The raw token string is <em>never</em> stored here; only the SHA-256 hash is persisted
 * to enable revocation lookups. The raw token is only ever transmitted to the invitee via email.
 *
 * <p>Status transitions: {@code PENDING → USED}, {@code PENDING → REVOKED}.
 */
public class InviteToken extends AggregateRoot<InviteTokenId> {

    private final InviteTokenId id;
    private final MeetingId meetingId;
    private final InviteeId inviteeId;
    private final String tokenHash;
    private InviteTokenStatus status;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private InviteToken(
            InviteTokenId id,
            MeetingId meetingId,
            InviteeId inviteeId,
            String tokenHash,
            InviteTokenStatus status,
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

    /**
     * Factory method — creates a new PENDING invite token.
     *
     * @param meetingId  the meeting this token grants access to
     * @param inviteeId  the specific invitee this token belongs to
     * @param tokenHash  SHA-256 hash of the raw token string
     * @param expiresAt  must be a future instant
     * @return new token in PENDING status
     * @throws IllegalArgumentException if {@code expiresAt} is not in the future
     */
    public static Result<InviteToken, MeetingError> create(
            MeetingId meetingId, InviteeId inviteeId, String tokenHash, Instant expiresAt) {
        if (!expiresAt.isAfter(Instant.now())) {
            return Result.failure(new MeetingError.InvalidSettings(
                    "InviteToken expiresAt must be in the future"));
        }
        Instant now = Instant.now();
        return Result.success(new InviteToken(
                InviteTokenId.of(UuidCreator.getTimeOrderedEpoch()),
                meetingId,
                inviteeId,
                tokenHash,
                InviteTokenStatus.PENDING,
                expiresAt,
                now,
                now));
    }

    /**
     * Reconstitution factory used by the persistence adapter.
     */
    public static InviteToken reconstitute(
            InviteTokenId id,
            MeetingId meetingId,
            InviteeId inviteeId,
            String tokenHash,
            InviteTokenStatus status,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt) {
        return new InviteToken(
                id, meetingId, inviteeId, tokenHash, status, expiresAt, createdAt, updatedAt);
    }

    /**
     * Transitions this token to the {@code USED} status.
     *
     * @return {@link Result#success()} or {@link Result#failure} with {@link MeetingError.InvalidSettings}
     * when the current status does not allow a USED transition
     */
    public Result<Void, MeetingError> markUsed() {
        if (!status.canTransitionTo(InviteTokenStatus.USED)) {
            return Result.failure(new MeetingError.InvalidSettings(
                    "InviteToken cannot transition from " + status + " to USED"));
        }
        status = InviteTokenStatus.USED;
        updatedAt = Instant.now();
        return Result.success();
    }

    /**
     * Transitions this token to the {@code REVOKED} status.
     *
     * @return {@link Result#success()} or {@link Result#failure} with {@link MeetingError.InvalidSettings}
     * when the current status does not allow a REVOKED transition
     */
    public Result<Void, MeetingError> revoke() {
        if (!status.canTransitionTo(InviteTokenStatus.REVOKED)) {
            return Result.failure(new MeetingError.InvalidSettings(
                    "InviteToken cannot transition from " + status + " to REVOKED"));
        }
        status = InviteTokenStatus.REVOKED;
        updatedAt = Instant.now();
        return Result.success();
    }

    @Override
    public InviteTokenId getId() {
        return id;
    }

    public MeetingId getMeetingId() {
        return meetingId;
    }

    public InviteeId getInviteeId() {
        return inviteeId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public InviteTokenStatus getStatus() {
        return status;
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
}
