package io.github.smiskinext.meetingmanagement.application.response;

import io.github.smiskinext.meetingmanagement.domain.model.InviteeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Response DTO for the invitee list endpoint.
 *
 * <p>Extends the base {@link InviteeResponse} with invite token metadata so the host can
 * see at a glance whether each invitee has a valid token.
 */
@Schema(description = "Invitee details including current invite token status")
public record InviteeListResponse(
        @Schema(description = "The invitee UUID") UUID inviteeId,

        @Schema(description = "Registered user ID, null for external email invitees") @Nullable UUID userId,

        @Schema(description = "Invitee email address") String email,

        @Schema(description = "Invitee display name if available") @Nullable String displayName,

        @Schema(description = "Invitee RSVP status") InviteeStatus status,
        @Schema(description = "When the invite was sent") Instant invitedAt,

        @Schema(description = "When the invitee responded, null if not yet responded") @Nullable Instant respondedAt,

        @Schema(description = "Current invite token status: PENDING, USED, REVOKED, or EXPIRED")
        @Nullable String tokenStatus) {}
