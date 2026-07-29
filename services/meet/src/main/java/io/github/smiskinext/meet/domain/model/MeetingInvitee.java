package io.github.smiskinext.meet.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.event.InviteeAcceptedEvent;
import io.github.smiskinext.meet.domain.event.InviteeDeclinedEvent;
import io.github.smiskinext.meet.domain.event.InviteeTentativeEvent;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.Email;
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
 * {@code accountId} is the resolved account identifier (always present — invitees must be
 * resolved to a Jira account); {@code email} is the stable invite key.
 *
 * <p>Status transitions: {@code NEEDS_ACTION → ACCEPTED}, {@code NEEDS_ACTION → DECLINED},
 * {@code NEEDS_ACTION → TENTATIVE}, {@code TENTATIVE → ACCEPTED/DECLINED},
 * {@code ACCEPTED → DECLINED/TENTATIVE}.
 *
 * <p>{@code role} and {@code rsvp} mirror the iCalendar (RFC 5545) ROLE and RSVP attendee
 * parameters: {@code role} declares the participation expectation and {@code rsvp} whether a
 * response is requested from the invitee.
 */
public class MeetingInvitee extends AggregateRoot<InviteeId> {

    private final TenantId tenantId;
    private final InviteeId id;
    private final MeetingId meetingId;
    private final InviterId inviterId;
    private final AccountId accountId;
    private final Email email;
    private final InviteeDisplayName displayName;
    private final InviteeRole role;
    private final boolean rsvp;
    private InviteeStatus status;
    private final Instant invitedAt;
    private @Nullable Instant respondedAt;
    private @Nullable Instant removedAt;

    private MeetingInvitee(
            TenantId tenantId,
            InviteeId id,
            MeetingId meetingId,
            InviterId inviterId,
            AccountId accountId,
            Email email,
            InviteeDisplayName displayName,
            InviteeRole role,
            boolean rsvp,
            InviteeStatus status,
            Instant invitedAt,
            @Nullable Instant respondedAt,
            @Nullable Instant removedAt) {
        this.tenantId = tenantId;
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

    /**
     * Factory method — creates a new NEEDS_ACTION invitation.
     */
    public static MeetingInvitee create(
            TenantId tenantId,
            MeetingId meetingId,
            InviterId inviterId,
            AccountId accountId,
            Email email,
            InviteeDisplayName displayName,
            InviteeRole role,
            boolean rsvp) {
        return new MeetingInvitee(
                tenantId,
                InviteeId.of(UuidCreator.getTimeOrderedEpoch()),
                meetingId,
                inviterId,
                accountId,
                email,
                displayName,
                role,
                rsvp,
                InviteeStatus.NEEDS_ACTION,
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
            AccountId accountId,
            Email email,
            InviteeDisplayName displayName,
            InviteeRole role,
            boolean rsvp,
            InviteeStatus status,
            Instant invitedAt,
            @Nullable Instant respondedAt,
            @Nullable Instant removedAt) {
        return new MeetingInvitee(
                tenantId,
                id,
                meetingId,
                inviterId,
                accountId,
                email,
                displayName,
                role,
                rsvp,
                status,
                invitedAt,
                respondedAt,
                removedAt);
    }

    public void remove() {
        if (removedAt == null) {
            removedAt = Instant.now();
        }
    }

    /**
     * Accepts the invitation, embedding the supplied meeting context into the enriched event.
     *
     * @param context meeting context used to build the iCalendar reply
     * @return {@code Result.success()} on success, or {@code Result.failure(InvalidInviteeTransition)}
     * if the current status does not allow transitioning to ACCEPTED
     */
    public Result<Void, MeetingError> accept(MeetingContext context) {
        if (removedAt != null || !status.canTransitionTo(InviteeStatus.ACCEPTED)) {
            return Result.failure(
                    new MeetingError.InvalidInviteeTransition(status, InviteeStatus.ACCEPTED));
        }
        status = InviteeStatus.ACCEPTED;
        respondedAt = Instant.now();
        registerEvent(new InviteeAcceptedEvent(
                UUID.randomUUID(),
                tenantId.value(),
                meetingId.value(),
                inviterId.value(),
                id.value(),
                email.value(),
                InviteeStatus.ACCEPTED.name(),
                respondedAt,
                context.meetingTitle(),
                context.startTime(),
                context.endTime(),
                context.zoneId(),
                context.organizerEmail(),
                context.organizerDisplayName(),
                displayName.value(),
                context.calendarUid(),
                context.calendarSequence()));
        return Result.success();
    }

    /**
     * Declines the invitation, embedding the supplied meeting context into the enriched event.
     *
     * @param context meeting context used to build the iCalendar reply
     * @return {@code Result.success()} on success, or {@code Result.failure(InvalidInviteeTransition)}
     * if the current status does not allow transitioning to DECLINED
     */
    public Result<Void, MeetingError> decline(MeetingContext context) {
        if (removedAt != null || !status.canTransitionTo(InviteeStatus.DECLINED)) {
            return Result.failure(
                    new MeetingError.InvalidInviteeTransition(status, InviteeStatus.DECLINED));
        }
        status = InviteeStatus.DECLINED;
        respondedAt = Instant.now();
        registerEvent(new InviteeDeclinedEvent(
                UUID.randomUUID(),
                tenantId.value(),
                meetingId.value(),
                inviterId.value(),
                id.value(),
                email.value(),
                InviteeStatus.DECLINED.name(),
                respondedAt,
                context.meetingTitle(),
                context.startTime(),
                context.endTime(),
                context.zoneId(),
                context.organizerEmail(),
                context.organizerDisplayName(),
                displayName.value(),
                context.calendarUid(),
                context.calendarSequence()));
        return Result.success();
    }

    /**
     * Tentatively responds to the invitation, embedding the supplied meeting context into the
     * enriched event.
     *
     * @param context meeting context used to build the iCalendar reply
     * @return {@code Result.success()} on success, or {@code Result.failure(InvalidInviteeTransition)}
     * if the current status does not allow transitioning to TENTATIVE
     */
    public Result<Void, MeetingError> tentative(MeetingContext context) {
        if (removedAt != null || !status.canTransitionTo(InviteeStatus.TENTATIVE)) {
            return Result.failure(
                    new MeetingError.InvalidInviteeTransition(status, InviteeStatus.TENTATIVE));
        }
        status = InviteeStatus.TENTATIVE;
        respondedAt = Instant.now();
        registerEvent(new InviteeTentativeEvent(
                UUID.randomUUID(),
                tenantId.value(),
                meetingId.value(),
                inviterId.value(),
                id.value(),
                email.value(),
                InviteeStatus.TENTATIVE.name(),
                respondedAt,
                context.meetingTitle(),
                context.startTime(),
                context.endTime(),
                context.zoneId(),
                context.organizerEmail(),
                context.organizerDisplayName(),
                displayName.value(),
                context.calendarUid(),
                context.calendarSequence()));
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

    public AccountId getAccountId() {
        return accountId;
    }

    public Email getEmail() {
        return email;
    }

    public InviteeDisplayName getDisplayName() {
        return displayName;
    }

    public InviteeRole getRole() {
        return role;
    }

    public boolean isRsvp() {
        return rsvp;
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

    public Optional<Instant> getRemovedAt() {
        return Optional.ofNullable(removedAt);
    }
}
