package io.github.smiskinext.meet.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.event.JoinRequestApprovedEvent;
import io.github.smiskinext.meet.domain.event.JoinRequestCreatedEvent;
import io.github.smiskinext.meet.domain.event.JoinRequestDeniedEvent;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.JoinRequestId;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.shared.domain.AggregateRoot;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Represents a participant's request to join a meeting that requires manual approval.
 *
 * <p>Stored exclusively in Redis with a TTL. Not persisted to PostgreSQL.
 * Identified by {@code accountId} for the requesting participant and {@code deviceId}
 * for the specific device that initiated the request.
 *
 * <p>Status transitions:
 * <ul>
 *   <li>{@code PENDING → APPROVED} (host approves)</li>
 *   <li>{@code PENDING → DENIED} (host denies)</li>
 *   <li>{@code PENDING → EXPIRED} (TTL elapsed)</li>
 *   <li>{@code APPROVED → APPROVED} (idempotent re-approval)</li>
 *   <li>{@code DENIED → DENIED} (idempotent re-denial)</li>
 * </ul>
 *
 * <p>Invalid transitions (e.g., {@code DENIED → APPROVED}) return
 * {@code Result.failure(InvalidJoinRequestTransition)}.
 */
public class JoinRequest extends AggregateRoot<JoinRequestId> {

    private final JoinRequestId id;
    private final MeetingId meetingId;
    private final AccountId accountId;
    private final String displayName;
    private final String deviceId;
    private final @Nullable String avatarUrl;
    private JoinRequestStatus status;
    private final Instant requestedAt;
    private final Instant expiresAt;

    private JoinRequest(
            JoinRequestId id,
            MeetingId meetingId,
            AccountId accountId,
            String displayName,
            String deviceId,
            @Nullable String avatarUrl,
            JoinRequestStatus status,
            Instant requestedAt,
            Instant expiresAt) {
        this.id = id;
        this.meetingId = meetingId;
        this.accountId = accountId;
        this.displayName = displayName;
        this.deviceId = deviceId;
        this.avatarUrl = avatarUrl;
        this.status = status;
        this.requestedAt = requestedAt;
        this.expiresAt = expiresAt;
    }

    /**
     * Factory method — creates a new PENDING join request.
     */
    public static JoinRequest create(
            MeetingId meetingId,
            AccountId accountId,
            String displayName,
            String deviceId,
            @Nullable String avatarUrl,
            Instant expiresAt) {
        return new JoinRequest(
                JoinRequestId.of(UuidCreator.getTimeOrderedEpoch()),
                meetingId,
                accountId,
                displayName,
                deviceId,
                avatarUrl,
                JoinRequestStatus.PENDING,
                Instant.now(),
                expiresAt);
    }

    /**
     * Reconstitution factory used by the Redis repository adapter.
     */
    public static JoinRequest reconstitute(
            JoinRequestId id,
            MeetingId meetingId,
            AccountId accountId,
            String displayName,
            String deviceId,
            @Nullable String avatarUrl,
            JoinRequestStatus status,
            Instant requestedAt,
            Instant expiresAt) {
        return new JoinRequest(
                id,
                meetingId,
                accountId,
                displayName,
                deviceId,
                avatarUrl,
                status,
                requestedAt,
                expiresAt);
    }

    /**
     * Registers a {@link JoinRequestCreatedEvent} for publication through the transactional outbox.
     *
     * <p>The tenant identifier lives outside the aggregate (join requests are Redis-only and carry
     * no tenant column), so it is supplied by the application layer at creation time.
     *
     * @param tenantId the tenant that owns the meeting the request targets
     */
    public void registerCreatedEvent(String tenantId) {
        registerEvent(new JoinRequestCreatedEvent(
                UUID.randomUUID(),
                tenantId,
                meetingId.value(),
                id.value(),
                accountId.value(),
                displayName,
                deviceId,
                avatarUrl,
                requestedAt));
    }

    /**
     * Registers a {@link JoinRequestApprovedEvent} for publication through the transactional outbox.
     *
     * <p>The tenant identifier and approving account live outside the aggregate (join requests are
     * Redis-only and carry no tenant or actor column), so they are supplied by the application layer
     * at decision time. The requester's account and device are sourced from the aggregate so the
     * requester-facing stream can correlate the outcome.
     *
     * @param tenantId     the tenant that owns the meeting the request targets
     * @param liveKitToken the issued LiveKit access token the requester uses to connect
     * @param roomName     the LiveKit room name the requester joins
     * @param approvedBy   the account of the host that approved the request
     */
    public void registerApprovedEvent(
            String tenantId, String liveKitToken, String roomName, String approvedBy) {
        registerEvent(new JoinRequestApprovedEvent(
                UUID.randomUUID(),
                tenantId,
                meetingId.value(),
                id.value(),
                accountId.value(),
                deviceId,
                liveKitToken,
                roomName,
                approvedBy,
                Instant.now()));
    }

    /**
     * Registers a {@link JoinRequestDeniedEvent} for publication through the transactional outbox.
     *
     * <p>The tenant identifier and denying account live outside the aggregate, so they are supplied
     * by the application layer at decision time. The requester's account and device are sourced from
     * the aggregate so the requester-facing stream can correlate the outcome.
     *
     * @param tenantId the tenant that owns the meeting the request targets
     * @param deniedBy the account of the host that denied the request
     */
    public void registerDeniedEvent(String tenantId, String deniedBy) {
        registerEvent(new JoinRequestDeniedEvent(
                UUID.randomUUID(),
                tenantId,
                meetingId.value(),
                id.value(),
                accountId.value(),
                deviceId,
                deniedBy,
                Instant.now()));
    }

    /**
     * Approves the join request.
     *
     * @return {@code Result.success()} on success (including idempotent re-approval),
     * or {@code Result.failure(InvalidJoinRequestTransition)} if the current status
     * does not allow transitioning to APPROVED (e.g., DENIED → APPROVED)
     */
    public Result<Void, MeetingError> approve() {
        if (status == JoinRequestStatus.APPROVED) {
            return Result.success();
        }
        if (status == JoinRequestStatus.DENIED) {
            return Result.failure(new MeetingError.InvalidJoinRequestTransition(
                    status, JoinRequestStatus.APPROVED));
        }
        status = JoinRequestStatus.APPROVED;
        return Result.success();
    }

    /**
     * Denies the join request.
     *
     * @return {@code Result.success()} on success (including idempotent re-denial),
     * or {@code Result.failure(InvalidJoinRequestTransition)} if the current status
     * does not allow transitioning to DENIED (e.g., APPROVED → DENIED)
     */
    public Result<Void, MeetingError> deny() {
        if (status == JoinRequestStatus.DENIED) {
            return Result.success();
        }
        if (status == JoinRequestStatus.APPROVED) {
            return Result.failure(new MeetingError.InvalidJoinRequestTransition(
                    status, JoinRequestStatus.DENIED));
        }
        status = JoinRequestStatus.DENIED;
        return Result.success();
    }

    /**
     * Marks the join request as expired.
     *
     * <p>Called by the cleanup job when the TTL elapses.
     */
    public void expire() {
        if (status == JoinRequestStatus.PENDING) {
            status = JoinRequestStatus.EXPIRED;
        }
    }

    @Override
    public JoinRequestId getId() {
        return id;
    }

    public MeetingId getMeetingId() {
        return meetingId;
    }

    public AccountId getAccountId() {
        return accountId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public @Nullable String getAvatarUrl() {
        return avatarUrl;
    }

    public JoinRequestStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
