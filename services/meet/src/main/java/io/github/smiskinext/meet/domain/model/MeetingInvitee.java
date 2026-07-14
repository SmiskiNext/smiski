package io.github.smiskinext.meet.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.event.InviteeAcceptedEvent;
import io.github.smiskinext.meet.domain.event.InviteeDeclinedEvent;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.Email;
import io.github.smiskinext.meet.domain.model.valueobject.InviteToken;
import io.github.smiskinext.meet.domain.model.valueobject.InviteeDisplayName;
import io.github.smiskinext.meet.domain.model.valueobject.InviteeId;
import io.github.smiskinext.meet.domain.model.valueobject.InviterId;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.shared.domain.AggregateRoot;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Represents a pre-scheduled invitation for a meeting.
 *
 * <p>Created at scheduling time when the host provides an invitee list.
 * {@code accountId} is the resolved account identifier; {@code email} is the stable invite key.
 *
 * <p>Status transitions: {@code PENDING → ACCEPTED}, {@code PENDING → DECLINED},
 * {@code ACCEPTED → DECLINED}.
 *
 * <p>The invite token is carried inline on the invitee: each invitee holds at most one token,
 * so there is exactly one active invite code at any point in time. Rotating an invite overwrites
 * the token fields; no token history is retained.
 */
public class MeetingInvitee extends AggregateRoot<InviteeId> {

    private final TenantId tenantId;
    private final InviteeId id;
    private final MeetingId meetingId;
    private final InviterId inviterId;
    private @Nullable AccountId accountId;
    private final Email email;
    private final @Nullable InviteeDisplayName displayName;
    private InviteeStatus status;
    private final Instant invitedAt;
    private @Nullable Instant respondedAt;
    private @Nullable InviteToken inviteToken;

    private MeetingInvitee(
            TenantId tenantId,
            InviteeId id,
            MeetingId meetingId,
            InviterId inviterId,
            @Nullable AccountId accountId,
            Email email,
            @Nullable InviteeDisplayName displayName,
            InviteeStatus status,
            Instant invitedAt,
            @Nullable Instant respondedAt,
            @Nullable InviteToken inviteToken) {
        this.tenantId = tenantId;
        this.id = id;
        this.meetingId = meetingId;
        this.inviterId = inviterId;
        this.accountId = accountId;
        this.email = email;
        this.displayName = displayName;
        this.status = status;
        this.invitedAt = invitedAt;
        this.respondedAt = respondedAt;
        this.inviteToken = inviteToken;
    }

    /**
     * Factory method — creates a new PENDING invitation without an invite token.
     * Call {@link #assignToken(String, Instant)} after generating the token.
     */
    public static MeetingInvitee create(
            TenantId tenantId,
            MeetingId meetingId,
            InviterId inviterId,
            @Nullable AccountId accountId,
            Email email,
            @Nullable InviteeDisplayName displayName) {
        return new MeetingInvitee(
                tenantId,
                InviteeId.of(UuidCreator.getTimeOrderedEpoch()),
                meetingId,
                inviterId,
                accountId,
                email,
                displayName,
                InviteeStatus.PENDING,
                Instant.now(),
                null,
                null);
    }

    /**
     * Reconstitution factory used by the persistence adapter.
     */
    public static MeetingInvitee reconstitute(
            TenantId tenantId,
            InviteeId id,
            MeetingId meetingId,
            InviterId inviterId,
            @Nullable AccountId accountId,
            Email email,
            @Nullable InviteeDisplayName displayName,
            InviteeStatus status,
            Instant invitedAt,
            @Nullable Instant respondedAt,
            @Nullable InviteToken inviteToken) {
        return new MeetingInvitee(
                tenantId,
                id,
                meetingId,
                inviterId,
                accountId,
                email,
                displayName,
                status,
                invitedAt,
                respondedAt,
                inviteToken);
    }

    /**
     * Assigns a fresh invite token to this invitee, guaranteeing a single active invite code.
     *
     * @param tokenHash SHA-256 hash of the raw token string (the raw token is never stored)
     * @param expiresAt future instant at which the token expires
     * @return {@code Result.success()} on success, or {@code Result.failure(InvalidInviteToken)}
     *     when a PENDING token already exists or {@code expiresAt} is not in the future
     */
    public Result<Void, MeetingError> assignToken(String tokenHash, Instant expiresAt) {
        if (inviteToken != null && inviteToken.status() == InviteTokenStatus.PENDING) {
            return Result.failure(
                    new MeetingError.InvalidInviteToken(
                            "Invitee already has an active invite token; revoke it before assigning a new one"));
        }
        return InviteToken.issue(tokenHash, expiresAt).map(token -> {
            this.inviteToken = token;
            return null;
        });
    }

    /**
     * Transitions the invite token to {@code USED}.
     *
     * @return {@code Result.success()} on success, or {@code Result.failure(InvalidInviteToken)}
     *     when no token is present or the current status forbids the transition
     */
    public Result<Void, MeetingError> markTokenUsed() {
        if (inviteToken == null) {
            return Result.failure(
                    new MeetingError.InvalidInviteToken("Invitee has no invite token"));
        }
        return inviteToken.markUsed().map(token -> {
            this.inviteToken = token;
            return null;
        });
    }

    /**
     * Transitions the invite token to {@code REVOKED}.
     *
     * @return {@code Result.success()} on success, or {@code Result.failure(InvalidInviteToken)}
     *     when no token is present or the current status forbids the transition
     */
    public Result<Void, MeetingError> revokeToken() {
        if (inviteToken == null) {
            return Result.failure(
                    new MeetingError.InvalidInviteToken("Invitee has no invite token"));
        }
        return inviteToken.revoke().map(token -> {
            this.inviteToken = token;
            return null;
        });
    }

    /**
     * Accepts the invitation.
     *
     * @return {@code Result.success()} on success, or {@code Result.failure(InvalidInviteeTransition)}
     * if the current status does not allow transitioning to ACCEPTED
     */
    public Result<Void, MeetingError> accept() {
        if (!status.canTransitionTo(InviteeStatus.ACCEPTED)) {
            return Result.failure(
                    new MeetingError.InvalidInviteeTransition(status, InviteeStatus.ACCEPTED));
        }
        status = InviteeStatus.ACCEPTED;
        respondedAt = Instant.now();
        registerEvent(new InviteeAcceptedEvent(
                UUID.randomUUID(),
                tenantId.value(),
                id.value(),
                meetingId.value(),
                inviterId.value(),
                respondedAt));
        return Result.success();
    }

    /**
     * Declines the invitation.
     *
     * @return {@code Result.success()} on success, or {@code Result.failure(InvalidInviteeTransition)}
     * if the current status does not allow transitioning to DECLINED
     */
    public Result<Void, MeetingError> decline() {
        if (!status.canTransitionTo(InviteeStatus.DECLINED)) {
            return Result.failure(
                    new MeetingError.InvalidInviteeTransition(status, InviteeStatus.DECLINED));
        }
        status = InviteeStatus.DECLINED;
        respondedAt = Instant.now();
        registerEvent(new InviteeDeclinedEvent(
                UUID.randomUUID(),
                tenantId.value(),
                id.value(),
                meetingId.value(),
                inviterId.value(),
                respondedAt));
        return Result.success();
    }

    @Override
    public InviteeId getId() {
        return id;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public MeetingId getMeetingId() {
        return meetingId;
    }

    public InviterId getInviterId() {
        return inviterId;
    }

    public Optional<AccountId> getAccountId() {
        return Optional.ofNullable(accountId);
    }

    public Email getEmail() {
        return email;
    }

    public Optional<InviteeDisplayName> getDisplayName() {
        return Optional.ofNullable(displayName);
    }

    public InviteeStatus getStatus() {
        return status;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public Optional<Instant> getRespondedAt() {
        return Optional.ofNullable(respondedAt);
    }

    /**
     * Returns the current invite token, if one has been assigned.
     */
    public Optional<InviteToken> getInviteToken() {
        return Optional.ofNullable(inviteToken);
    }
}
