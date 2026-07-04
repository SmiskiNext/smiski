package io.github.smiskinext.meetingmanagement.application.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Response DTO returned by the invite token validation endpoint.
 *
 * <p>On success, contains the meeting details needed to proceed with joining.
 * {@code preApproved} is {@code true} when the meeting's admission policy allows the
 * token holder to join without a separate join-request approval step.
 */
@Schema(
        description =
                "Result of a successful invite token validation, containing meeting join details")
public record ValidateInviteTokenResponse(
        @Schema(description = "The meeting UUID") UUID meetingId,

        @Schema(description = "The meeting short code for joining via WebRTC signalling")
        String shortCode,

        @Schema(
                description =
                        "True when the invitee can join directly without waiting-room approval")
        boolean preApproved) {}
