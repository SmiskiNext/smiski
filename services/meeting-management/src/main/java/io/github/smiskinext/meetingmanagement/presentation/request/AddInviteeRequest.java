package io.github.smiskinext.meetingmanagement.presentation.request;

import io.github.smiskinext.meetingmanagement.application.command.AddInviteeCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Request body for the add-invitee endpoint.
 *
 * @param email       the invitee's email address (required, must be a valid email)
 * @param displayName optional display name for the invitee
 */
@Schema(description = "Request body for adding an invitee to a scheduled meeting")
public record AddInviteeRequest(
        @Schema(description = "Invitee email address") @NotBlank @Email @Size(max = 255) String email,

        @Schema(description = "Optional display name for the invitee") @Nullable @Size(max = 128) String displayName) {

    public AddInviteeCommand toCommand(UUID meetingId, UUID requesterId) {
        return new AddInviteeCommand(meetingId, email, displayName, requesterId);
    }
}
