package io.github.smiskinext.meetingmanagement.application.response;

import io.github.smiskinext.meetingmanagement.domain.model.InviteeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Invitee response status after accepting or declining an invitation")
public record InviteeRespondResponse(
        @Schema(description = "Meeting ID") UUID meetingId,
        @Schema(description = "Registered invitee user ID") UUID userId,
        @Schema(description = "Invitee RSVP status") InviteeStatus status,
        @Schema(description = "When the invitee responded") Instant respondedAt) {}
