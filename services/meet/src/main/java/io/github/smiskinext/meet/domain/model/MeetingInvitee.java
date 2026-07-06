package io.github.smiskinext.meet.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.event.InviteeAcceptedEvent;
import io.github.smiskinext.meet.domain.event.InviteeDeclinedEvent;
import io.github.smiskinext.meet.domain.model.valueobject.InviteTokenId;
import io.github.smiskinext.meet.domain.model.valueobject.InviteeDisplayName;
import io.github.smiskinext.meet.domain.model.valueobject.InviteeId;
import io.github.smiskinext.meet.domain.model.valueobject.InviterId;
import io.github.smiskinext.shared.domain.AggregateRoot;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.Email;
import io.github.smiskinext.shared.domain.valueobject.MeetingId;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Represents a pre-scheduled invitation for a meeting.
 *
 * <p>Created at scheduling time when the host provides an invitee list.
 * {@code userId} is populated from gRPC resolution; {@code email} is the stable invite key.
 *
 * <p>Status transitions: {@code PENDING → ACCEPTED}, {@code PENDING → DECLINED},
 * {@code ACCEPTED → DECLINED}.
 */
public class MeetingInvitee extends AggregateRoot<InviteeId> {

    private final InviteeId id;
    private final MeetingId meetingId;
    private final InviterId inviterId;
    private @Nullable UserId userId;
    private final Email email;
    private final @Nullable InviteeDisplayName displayName;
    private InviteeStatus status;
    private final Instant invitedAt;
    private @Nullable Instant respondedAt;
    private @Nullable InviteTokenId inviteTokenId;

    private MeetingInvitee(
            InviteeId id,
            MeetingId meetingId,
            InviterId inviterId,
            @Nullable UserId userId,
            Email email,
            @Nullable InviteeDisplayName displayName,
            InviteeStatus status,
            Instant invitedAt,
            @Nullable Instant respondedAt,
            @Nullable InviteTokenId inviteTokenId) {
        this.id = id;
        this.meetingId = meetingId;
        this.inviterId = inviterId;
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.status = status;
        this.invitedAt = invitedAt;
        this.respondedAt = respondedAt;
        this.inviteTokenId = inviteTokenId;
    }

    /**
     * Factory method — creates a new PENDING invitation without an invite token.
     * Call {@link #assignInviteToken(InviteTokenId)} after token creation.
     */
    public static MeetingInvitee create(
            MeetingId meetingId,
            InviterId inviterId,
            @Nullable UserId userId,
            Email email,
            @Nullable InviteeDisplayName displayName) {
        return new MeetingInvitee(
                InviteeId.of(UuidCreator.getTimeOrderedEpoch()),
                meetingId,
                inviterId,
                userId,
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
            InviteeId id,
            MeetingId meetingId,
            InviterId inviterId,
            @Nullable UserId userId,
            Email email,
            @Nullable InviteeDisplayName displayName,
            InviteeStatus status,
            Instant invitedAt,
            @Nullable Instant respondedAt,
            @Nullable InviteTokenId inviteTokenId) {
        return new MeetingInvitee(
                id,
                meetingId,
                inviterId,
                userId,
                email,
                displayName,
                status,
                invitedAt,
                respondedAt,
                inviteTokenId);
    }

    /**
     * Associates an invite token with this invitee. Should be called once, right after token creation.
     */
    public void assignInviteToken(InviteTokenId tokenId) {
        this.inviteTokenId = tokenId;
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
                UUID.randomUUID(), id.value(), meetingId.value(), inviterId.value(), respondedAt));
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
                UUID.randomUUID(), id.value(), meetingId.value(), inviterId.value(), respondedAt));
        return Result.success();
    }

    @Override
    public InviteeId getId() {
        return id;
    }

    public MeetingId getMeetingId() {
        return meetingId;
    }

    public InviterId getInviterId() {
        return inviterId;
    }

    public Optional<UserId> getUserId() {
        return Optional.ofNullable(userId);
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
     * Returns the associated invite token ID, if one has been assigned.
     */
    public Optional<InviteTokenId> getInviteTokenId() {
        return Optional.ofNullable(inviteTokenId);
    }
}
