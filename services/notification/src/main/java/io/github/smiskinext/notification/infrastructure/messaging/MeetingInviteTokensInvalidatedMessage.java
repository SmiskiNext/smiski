package io.github.smiskinext.notification.infrastructure.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Kafka message contract for the {@code meeting-management.meeting.invite-tokens.invalidated}
 * topic.
 *
 * <p>Published when a host changes the password on a SCHEDULED meeting, causing all PENDING invite
 * tokens to be invalidated. The notification service sends a "your invite link has been updated"
 * email to each affected invitee listed in this event.
 */
public record MeetingInviteTokensInvalidatedMessage(
        UUID eventId,
        UUID aggregateId,
        UUID hostId,
        @Nullable String meetingTitle,
        String meetingShortCode,
        List<AffectedInviteeInfo> affectedInvitees,
        Instant updatedAt) {

    /**
     * Minimal info about an invitee whose invite token was invalidated.
     *
     * @param inviteeId   the ID of the MeetingInvitee record
     * @param userId      the resolved user ID (may be null)
     * @param email       the invitee's email address
     * @param displayName the invitee's display name (may be null)
     */
    public record AffectedInviteeInfo(
            UUID inviteeId,
            @Nullable UUID userId,
            String email,
            @Nullable String displayName) {}
}
