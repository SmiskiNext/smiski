package io.github.smiskinext.notification.infrastructure.messaging;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.smiskinext.notification.infrastructure.email.MeetingInvitationLinkFactory;
import org.jspecify.annotations.Nullable;

/**
 * Kafka message contract for the {@code meeting-management.meeting.invitations.sent} topic.
 *
 * <p>{@code rawPassword} has been removed from this contract. The notification service now
 * uses per-invitee invite tokens from {@code inviteeTokens} (userId → raw token) to build
 * the join link via {@link MeetingInvitationLinkFactory}.
 *
 * <p>During migration, producers may send events without the {@code inviteeTokens} field.
 * The link factory will fall back to a legacy short-code URL when no token is present.
 */
public record MeetingInvitationsSentMessage(
        UUID eventId,
        UUID aggregateId,
        @Nullable String meetingTitle,
        String meetingShortCode,
        @Nullable Instant startTime,
        List<InviteeInfo> invitees,
        @Nullable Map<UUID, String> inviteeTokens,
        Instant occurredAt) {

    public record InviteeInfo(
            @Nullable UUID userId, String email, @Nullable String displayName) {}
}
