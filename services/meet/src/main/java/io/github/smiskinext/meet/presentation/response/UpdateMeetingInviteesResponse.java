package io.github.smiskinext.meet.presentation.response;

import io.github.smiskinext.meet.application.result.UpdateMeetingInviteesResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(description = "Response containing the current active invitee list of a meeting")
public record UpdateMeetingInviteesResponse(List<Invitee> invitees) {

    @Schema(name = "MeetingInviteeSnapshot", description = "Meeting invitee snapshot")
    public record Invitee(
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
            @Nullable Instant respondedAt) {}

    public static UpdateMeetingInviteesResponse from(UpdateMeetingInviteesResult result) {
        List<Invitee> invitees = result.invitees().stream()
                .map(invitee -> new Invitee(
                        invitee.id(),
                        invitee.accountId(),
                        invitee.email(),
                        invitee.displayName(),
                        invitee.role(),
                        invitee.status(),
                        invitee.invitedAt(),
                        invitee.respondedAt()))
                .toList();
        return new UpdateMeetingInviteesResponse(invitees);
    }
}
