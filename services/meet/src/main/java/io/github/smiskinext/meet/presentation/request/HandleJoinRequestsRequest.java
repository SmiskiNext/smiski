package io.github.smiskinext.meet.presentation.request;

import io.github.smiskinext.meet.application.command.AcceptJoinRequestsCommand;
import io.github.smiskinext.meet.application.command.DeclineJoinRequestsCommand;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request body for a host accept/decline decision over one or more pending join requests. The
 * account and tenant are resolved from the request context, never from the body.
 */
@Schema(description = "Host decision over pending join requests")
public record HandleJoinRequestsRequest(
        @Schema(description = "Identifiers of the pending join requests to decide") @NotEmpty List<@NotNull UUID> requestIds) {

    public AcceptJoinRequestsCommand toAcceptCommand(
            UUID meetingId, String accountId, String tenantId) {
        return new AcceptJoinRequestsCommand(meetingId, tenantId, accountId, requestIds);
    }

    public DeclineJoinRequestsCommand toDeclineCommand(
            UUID meetingId, String accountId, String tenantId) {
        return new DeclineJoinRequestsCommand(meetingId, tenantId, accountId, requestIds);
    }
}
