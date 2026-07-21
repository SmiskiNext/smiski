package io.github.smiskinext.meet.presentation.request;

import io.github.smiskinext.meet.application.command.UpdateMeetingInviteesCommand;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Request body for replacing the invitee list of a meeting")
public record UpdateMeetingInviteesRequest(
        @Schema(description = "Full invitee list to synchronize") @NotNull @Valid List<@Valid Invitee> invitees) {

    @Schema(description = "Meeting invitee")
    public record Invitee(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank String accountId,
            @NotBlank @Size(max = 255) String displayName) {}

    @Schema(hidden = true)
    @AssertTrue(message = "invitees must not contain duplicate accountId values") public boolean isAccountIdsUnique() {
        if (invitees == null) {
            return true;
        }
        long distinct = invitees.stream()
                .map(Invitee::accountId)
                .filter(accountId -> accountId != null && !accountId.isBlank())
                .distinct()
                .count();
        long present = invitees.stream()
                .map(Invitee::accountId)
                .filter(accountId -> accountId != null && !accountId.isBlank())
                .count();
        return distinct == present;
    }

    public UpdateMeetingInviteesCommand toCommand(
            UUID meetingId, String accountId, String tenantId) {
        List<UpdateMeetingInviteesCommand.Invitee> inviteeCommands = invitees.stream()
                .map(invitee -> new UpdateMeetingInviteesCommand.Invitee(
                        invitee.email(), invitee.accountId(), invitee.displayName()))
                .toList();
        return new UpdateMeetingInviteesCommand(meetingId, accountId, tenantId, inviteeCommands);
    }
}
