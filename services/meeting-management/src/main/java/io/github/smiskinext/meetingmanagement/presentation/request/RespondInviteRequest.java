package io.github.smiskinext.meetingmanagement.presentation.request;

import io.github.smiskinext.meetingmanagement.application.command.InviteeResponseType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Invitation response request")
public record RespondInviteRequest(
        @NotNull @Schema(description = "Invitation response: ACCEPTED or DECLINED")
        InviteeResponseType response) {}
