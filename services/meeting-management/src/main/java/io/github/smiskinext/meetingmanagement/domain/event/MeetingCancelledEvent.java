package io.github.smiskinext.meetingmanagement.domain.event;

import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published when a meeting transitions SCHEDULED → CANCELLED.
 *
 * <p>{@code meetingTitle} and {@code startTime} are nullable because instant meetings may not have
 * a published title or scheduled start time at cancellation time.
 */
public record MeetingCancelledEvent(
        UUID eventId,
        UUID aggregateId,
        UUID hostId,
        @Nullable String meetingTitle,
        String meetingShortCode,
        @Nullable Instant startTime,
        List<InviteeInfo> invitees,
        Instant cancelledAt)
        implements PublishableEvent {

    public record InviteeInfo(
            @Nullable UUID userId,
            String email,
            @Nullable String displayName,
            String status,
            Instant invitedAt) {}

    @Override
    public String aggregateType() {
        return "meeting";
    }

    @Override
    public String eventType() {
        return "io.github.phunguy65.zms.meeting.cancelled.v1";
    }

    @Override
    public String topic() {
        return "meeting-management.meeting.cancelled";
    }

    @Override
    public Instant occurredAt() {
        return cancelledAt;
    }
}
