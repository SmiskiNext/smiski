package io.github.smiskinext.meet.domain.port;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Outbound port for the current "who is sharing their screen" state of a meeting.
 *
 * <p>This is ephemeral, best-effort real-time state, not part of the durable meeting record — it
 * lives in Redis rather than Postgres, mirroring how {@code JoinRequestRepository} keeps the
 * join-request queue out of the relational schema.
 */
public interface ScreenShareStateRepository {

    /**
     * Returns whether the given account is currently marked as sharing their screen in the
     * meeting.
     */
    boolean isSharing(UUID meetingId, String accountId);

    /**
     * Marks the account as currently sharing their screen in the meeting.
     */
    void markSharing(UUID meetingId, String accountId, Instant startedAt);

    /**
     * Clears the account's screen-sharing state in the meeting. A no-op if not currently sharing.
     */
    void clearSharing(UUID meetingId, String accountId);

    /**
     * Returns the account ids currently marked as sharing their screen in the meeting.
     */
    Set<String> findSharingAccountIds(UUID meetingId);
}
