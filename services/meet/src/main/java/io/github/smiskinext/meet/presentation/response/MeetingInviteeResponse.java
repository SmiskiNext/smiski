package io.github.smiskinext.meet.presentation.response;

import io.github.smiskinext.meet.application.result.AcceptMeetingInviteeResult;
import io.github.smiskinext.meet.application.result.DeclineMeetingInviteeResult;
import io.github.smiskinext.meet.application.result.TentativeMeetingInviteeResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Full snapshot of an invitee returned after a successful accept/decline/tentative response.
 */
@Schema(description = "Snapshot of the invitee after responding to the invitation")
public record MeetingInviteeResponse(
        UUID id,
        String accountId,
        String email,
        String displayName,
        String role,
        String status,
        Instant invitedAt,

        @Schema(
                description = "Timestamp when the invitee responded; null if not yet responded",
                nullable = true)
        @Nullable Instant respondedAt) {

    public static MeetingInviteeResponse from(AcceptMeetingInviteeResult result) {
        return new MeetingInviteeResponse(
                result.id(),
                result.accountId(),
                result.email(),
                result.displayName(),
                result.role(),
                result.status(),
                result.invitedAt(),
                result.respondedAt());
    }

    public static MeetingInviteeResponse from(DeclineMeetingInviteeResult result) {
        return new MeetingInviteeResponse(
                result.id(),
                result.accountId(),
                result.email(),
                result.displayName(),
                result.role(),
                result.status(),
                result.invitedAt(),
                result.respondedAt());
    }

    public static MeetingInviteeResponse from(TentativeMeetingInviteeResult result) {
        return new MeetingInviteeResponse(
                result.id(),
                result.accountId(),
                result.email(),
                result.displayName(),
                result.role(),
                result.status(),
                result.invitedAt(),
                result.respondedAt());
    }
}
