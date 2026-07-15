package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.shared.domain.PublishableEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published when a host invalidates the PENDING invite tokens on a SCHEDULED meeting. The
 * notification service consumes this event to alert affected invitees that their invite link has
 * been invalidated.
 *
 * <p>Topic: {@code meet.meeting.invite-tokens-invalidated}
 */
public record MeetingInviteTokensInvalidatedEvent(
        UUID eventId,
        String tenantId,
        UUID meetingId,
        String hostId,
        @Nullable String meetingTitle,
        String meetingShortCode,
        List<AffectedInviteeInfo> affectedInvitees,
        Instant updatedAt)
        implements PublishableEvent {

    /**
     * Minimal info about an invitee whose invite token was invalidated.
     *
     * @param inviteeId   the ID of the MeetingInvitee record
     * @param accountId   the resolved account ID (may be null)
     * @param email       the invitee's email address
     * @param displayName the invitee's display name (may be null)
     */
    public record AffectedInviteeInfo(
            UUID inviteeId,
            @Nullable String accountId,
            String email,
            @Nullable String displayName) {}

    @Override
    public String aggregateId() {
        return meetingId.toString();
    }

    @Override
    public String aggregateType() {
        return "meeting";
    }

    @Override
    public String eventType() {
        return "io.github.smiskinext.meet.meeting.invite-tokens-invalidated.v1";
    }

    @Override
    public String topic() {
        return "meet.meeting.invite-tokens-invalidated";
    }

    @Override
    public Instant occurredAt() {
        return updatedAt;
    }
}
