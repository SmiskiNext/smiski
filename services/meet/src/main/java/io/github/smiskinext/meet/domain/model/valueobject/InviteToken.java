package io.github.smiskinext.meet.domain.model.valueobject;

import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.InviteTokenStatus;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.ValueObject;

import java.time.Instant;
import java.util.Objects;

/**
 * The single invite token carried inline on a meeting invitee.
 *
 * <p>The raw token string is never stored; only its SHA-256 hash is kept to enable revocation and
 * validation lookups. As a value object it is immutable: lifecycle transitions return a new
 * instance rather than mutating in place.
 *
 * <p>Status transitions: {@code PENDING → USED}, {@code PENDING → REVOKED}. The {@code EXPIRED}
 * status is derived from {@link #expiresAt()} during validation and is never persisted directly.
 */
public record InviteToken(
        String tokenHash,
        InviteTokenStatus status,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt)
        implements ValueObject {

    public InviteToken {
        Objects.requireNonNull(tokenHash, "InviteToken tokenHash must not be null");
        Objects.requireNonNull(status, "InviteToken status must not be null");
        Objects.requireNonNull(expiresAt, "InviteToken expiresAt must not be null");
        Objects.requireNonNull(createdAt, "InviteToken createdAt must not be null");
        Objects.requireNonNull(updatedAt, "InviteToken updatedAt must not be null");
    }

    /**
     * Issues a fresh PENDING invite token.
     *
     * @param tokenHash SHA-256 hash of the raw token string
     * @param expiresAt future instant at which the token expires
     * @return a new PENDING token, or {@code Result.failure(InvalidInviteToken)} when
     *     {@code expiresAt} is not in the future
     */
    public static Result<InviteToken, MeetingError> issue(String tokenHash, Instant expiresAt) {
        if (!expiresAt.isAfter(Instant.now())) {
            return Result.failure(new MeetingError.InvalidInviteToken(
                    "Invite token expiresAt must be in the future"));
        }
        Instant now = Instant.now();
        return Result.success(
                new InviteToken(tokenHash, InviteTokenStatus.PENDING, expiresAt, now, now));
    }

    /**
     * Reconstitutes an invite token from persisted state.
     */
    public static InviteToken reconstitute(
            String tokenHash,
            InviteTokenStatus status,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt) {
        return new InviteToken(tokenHash, status, expiresAt, createdAt, updatedAt);
    }

    /**
     * Returns a copy transitioned to {@code USED}.
     *
     * @return the updated token, or {@code Result.failure(InvalidInviteToken)} when the current
     *     status forbids the transition
     */
    public Result<InviteToken, MeetingError> markUsed() {
        return transitionTo(InviteTokenStatus.USED);
    }

    /**
     * Returns a copy transitioned to {@code REVOKED}.
     *
     * @return the updated token, or {@code Result.failure(InvalidInviteToken)} when the current
     *     status forbids the transition
     */
    public Result<InviteToken, MeetingError> revoke() {
        return transitionTo(InviteTokenStatus.REVOKED);
    }

    /**
     * Returns {@code true} when the token is PENDING and not yet past its expiry.
     */
    public boolean isActive() {
        return status == InviteTokenStatus.PENDING && expiresAt.isAfter(Instant.now());
    }

    private Result<InviteToken, MeetingError> transitionTo(InviteTokenStatus target) {
        if (!status.canTransitionTo(target)) {
            return Result.failure(new MeetingError.InvalidInviteToken(
                    "Invite token cannot transition from " + status + " to " + target));
        }
        return Result.success(
                new InviteToken(tokenHash, target, expiresAt, createdAt, Instant.now()));
    }
}
