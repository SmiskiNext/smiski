package io.github.smiskinext.meet.presentation.response;

import io.github.smiskinext.meet.application.result.RemoveMeetingInviteesResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(description = "Response containing the invitees removed by a batch-delete call")
public record RemoveMeetingInviteesResponse(List<Invitee> invitees) {

    @Schema(
            name = "RemovedMeetingInviteeSnapshot",
            description = "Removed meeting invitee snapshot")
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

    public static RemoveMeetingInviteesResponse from(RemoveMeetingInviteesResult result) {
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
        return new RemoveMeetingInviteesResponse(invitees);
    }
}
