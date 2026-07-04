package io.github.smiskinext.notification.infrastructure.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record MeetingCancelledMessage(
        UUID eventId,
        UUID aggregateId,
        UUID hostId,
        @Nullable String meetingTitle,
        String meetingShortCode,
        @Nullable Instant startTime,
        List<InviteeInfo> invitees,
        Instant cancelledAt) {

    public record InviteeInfo(
            @Nullable UUID userId,
            String email,
            @Nullable String displayName,
            String status,
            Instant invitedAt) {}
}
