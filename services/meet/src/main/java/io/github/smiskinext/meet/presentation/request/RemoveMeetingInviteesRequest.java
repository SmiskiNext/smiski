package io.github.smiskinext.meet.presentation.request;

import io.github.smiskinext.meet.application.command.RemoveMeetingInviteesCommand;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

@Schema(description = "Request body for removing invitees from a meeting by invitee id")
public record RemoveMeetingInviteesRequest(
        @NotEmpty @Schema(description = "Identifiers of the invitees to remove; at least one is required")
        List<UUID> inviteeIds) {

    public RemoveMeetingInviteesCommand toCommand(
            UUID meetingId, String accountId, String tenantId) {
        return new RemoveMeetingInviteesCommand(meetingId, accountId, tenantId, inviteeIds);
    }
}
