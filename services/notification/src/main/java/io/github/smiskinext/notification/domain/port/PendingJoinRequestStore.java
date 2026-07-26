package io.github.smiskinext.notification.domain.port;

import io.github.smiskinext.notification.domain.model.PendingJoinRequest;

import java.util.List;
import java.util.UUID;

/**
 * Store of pending join requests keyed per meeting, shared across notification replicas so a host
 * subscribing on any replica can replay the current pending set regardless of which replica handled
 * the original event.
 *
 * <p>Entries carry a time-to-live aligned with the join request lifetime; reads return only
 * unexpired requests.
 */
public interface PendingJoinRequestStore {

    /**
     * Records (or refreshes) a pending join request under its meeting.
     *
     * @param request the pending request to retain
     */
    void upsert(PendingJoinRequest request);

    /**
     * Returns the unexpired pending join requests for a meeting.
     *
     * @param meetingId the meeting whose pending requests to read
     * @return the current unexpired pending requests (may be empty)
     */
    List<PendingJoinRequest> findPendingByMeetingId(UUID meetingId);
}
