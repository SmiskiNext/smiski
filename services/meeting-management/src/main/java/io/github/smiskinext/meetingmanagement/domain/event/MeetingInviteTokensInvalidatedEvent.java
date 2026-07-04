package io.github.smiskinext.meetingmanagement.domain.event;

import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published when a host changes the password on a SCHEDULED meeting, causing all PENDING
 * invite tokens to be revoked. The notification service consumes this event to alert affected
 * invitees that their invite link has been invalidated.
 *
 * <p>Topic: {@code meeting-management.meeting.invite-tokens.invalidated}
 */
public record MeetingInviteTokensInvalidatedEvent(
        UUID eventId,
        UUID aggregateId,
        UUID hostId,
        @Nullable String meetingTitle,
        String meetingShortCode,
        List<AffectedInviteeInfo> affectedInvitees,
        Instant updatedAt)
        implements PublishableEvent {

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

    @Override
    public String aggregateType() {
        return "meeting";
    }

    @Override
    public String eventType() {
        return "io.github.phunguy65.zms.meeting.invite-tokens.invalidated.v1";
    }

    @Override
    public String topic() {
        return "meeting-management.meeting.invite-tokens.invalidated";
    }

    @Override
    public Instant occurredAt() {
        return updatedAt;
    }
}
