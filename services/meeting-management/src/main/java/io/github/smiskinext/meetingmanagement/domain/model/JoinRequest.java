package io.github.smiskinext.meetingmanagement.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.JoinRequestId;
import io.github.smiskinext.shared.domain.AggregateRoot;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.MeetingId;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Represents a participant's request to join a meeting that requires manual approval.
 *
 * <p>Stored exclusively in Redis with a TTL. Not persisted to PostgreSQL.
 * Identified by {@code deviceId} for guests (unauthenticated users) and {@code userId}
 * for authenticated users.
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
    private final @Nullable UserId userId;
    private final String displayName;
    private final String deviceId;
    private JoinRequestStatus status;
    private final Instant requestedAt;
    private final Instant expiresAt;

    private JoinRequest(
            JoinRequestId id,
            MeetingId meetingId,
            @Nullable UserId userId,
            String displayName,
            String deviceId,
            JoinRequestStatus status,
            Instant requestedAt,
            Instant expiresAt) {
        this.id = id;
        this.meetingId = meetingId;
        this.userId = userId;
        this.displayName = displayName;
        this.deviceId = deviceId;
        this.status = status;
        this.requestedAt = requestedAt;
        this.expiresAt = expiresAt;
    }

    /**
     * Factory method — creates a new PENDING join request.
     */
    public static JoinRequest create(
            MeetingId meetingId,
            @Nullable UserId userId,
            String displayName,
            String deviceId,
            Instant expiresAt) {
        return new JoinRequest(
                JoinRequestId.of(UuidCreator.getTimeOrderedEpoch()),
                meetingId,
                userId,
                displayName,
                deviceId,
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
            @Nullable UserId userId,
            String displayName,
            String deviceId,
            JoinRequestStatus status,
            Instant requestedAt,
            Instant expiresAt) {
        return new JoinRequest(
                id, meetingId, userId, displayName, deviceId, status, requestedAt, expiresAt);
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

    public Optional<UserId> getUserId() {
        return Optional.ofNullable(userId);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDeviceId() {
        return deviceId;
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
