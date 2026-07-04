package io.github.smiskinext.notification.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record InviteeRespondedMessage(
        UUID eventId,
        UUID aggregateId,
        @Nullable UUID inviteeId,
        UUID meetingId,
        UUID inviterId,
        @Nullable String meetingTitle,
        @Nullable String inviteeEmail,
        @Nullable String inviteeDisplayName,
        Instant respondedAt,
        @Nullable Instant acceptedAt,
        @Nullable Instant declinedAt,
        @Nullable String responseType) {

    public UUID resolvedInviteeId() {
        return inviteeId != null ? inviteeId : aggregateId;
    }

    public Instant resolvedRespondedAt() {
        if (respondedAt != null) {
            return respondedAt;
        }
        if (acceptedAt != null) {
            return acceptedAt;
        }
        return declinedAt;
    }

    public String resolvedResponseType() {
        if (responseType != null && !responseType.isBlank()) {
            return responseType;
        }
        return acceptedAt != null ? "ACCEPTED" : "DECLINED";
    }
}
