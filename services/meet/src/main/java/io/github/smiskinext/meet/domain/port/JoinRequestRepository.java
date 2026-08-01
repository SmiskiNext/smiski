package io.github.smiskinext.meet.domain.port;

import io.github.smiskinext.meet.domain.model.JoinRequest;
import io.github.smiskinext.meet.domain.model.JoinRequestStatus;
import io.github.smiskinext.meet.domain.projection.JoinRequestSummary;
import io.github.smiskinext.shared.domain.OffsetPageResponse;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for managing join requests in Redis.
 *
 * <p>Join requests are ephemeral and stored exclusively in Redis with a TTL.
 * No PostgreSQL persistence.
 */
public interface JoinRequestRepository {

    /**
     * Saves a join request to Redis with the specified TTL.
     *
     * <p>Creates:
     * <ul>
     *   <li>{@code ZADD join_request:{meetingId}} (score=expiresAt ms)</li>
     *   <li>{@code HSET join_request_meta:{requestId}} (all fields)</li>
     *   <li>{@code SET join_request_device:{meetingId}:{deviceId}} (for duplicate detection)</li>
     * </ul>
     *
     * @param request the join request to save
     * @param ttl     the time-to-live for the request metadata
     */
    void save(JoinRequest request, Duration ttl);

    /**
     * Finds a join request by its ID.
     *
     * @param requestId the join request ID
     * @return the join request, or {@code Optional.empty()} if not found or expired
     */
    Optional<JoinRequest> findById(UUID requestId);

    /**
     * Finds a join request by meeting ID and device ID (for duplicate detection).
     *
     * @param meetingId the meeting ID
     * @param deviceId  the device ID
     * @return the join request, or {@code Optional.empty()} if not found
     */
    Optional<JoinRequest> findByDeviceId(UUID meetingId, String deviceId);

    /**
     * Finds all pending join requests for a meeting, ordered by {@code requestedAt} ascending.
     *
     * @param meetingId the meeting ID
     * @return list of pending join requests (may be empty)
     */
    List<JoinRequest> findPendingByMeetingId(UUID meetingId);

    OffsetPageResponse<JoinRequestSummary> findPendingSummariesByMeetingId(
            UUID meetingId, int offset, int pageSize);

    /**
     * Returns the total number of {@code PENDING} join requests for a meeting across all pages.
     *
     * @param meetingId the meeting ID
     * @return count of PENDING requests (0 if none)
     */
    long countPendingByMeetingId(UUID meetingId);

    /**
     * Updates the status of a join request.
     *
     * @param requestId the join request ID
     * @param status    the new status
     */
    void updateStatus(UUID requestId, JoinRequestStatus status);

    /**
     * Removes a join request from the queue and deletes its metadata.
     *
     * <p>Deletes:
     * <ul>
     *   <li>{@code ZREM join_request:{meetingId}}</li>
     *   <li>{@code DEL join_request_meta:{requestId}}</li>
     *   <li>{@code DEL join_request_device:{meetingId}:{deviceId}}</li>
     * </ul>
     *
     * @param meetingId the meeting ID
     * @param requestId the join request ID
     */
    void removeFromQueue(UUID meetingId, UUID requestId);

    /**
     * Deletes all join requests for a meeting (called when meeting ends).
     *
     * @param meetingId the meeting ID
     */
    void deleteAllByMeetingId(UUID meetingId);
}
