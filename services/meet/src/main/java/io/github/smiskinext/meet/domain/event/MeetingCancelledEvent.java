package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.shared.domain.PublishableEvent;
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
        String tenantId,
        UUID meetingId,
        String hostId,
        @Nullable String meetingTitle,
        String meetingShortCode,
        @Nullable Instant startTime,
        List<InviteeInfo> invitees,
        Instant cancelledAt)
        implements PublishableEvent {

    public record InviteeInfo(
            @Nullable String accountId,
            String email,
            @Nullable String displayName,
            String status,
            Instant invitedAt) {}

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
        return "io.github.smiskinext.meet.meeting.cancelled.v1";
    }

    @Override
    public String topic() {
        return "meet.meeting.cancelled";
    }

    @Override
    public Instant occurredAt() {
        return cancelledAt;
    }
}
