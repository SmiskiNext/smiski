package io.github.smiskinext.meetingmanagement.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the invite token validation endpoint.
 *
 * @param token the raw invite token string from the join link
 */
@Schema(description = "Request body for validating a raw invite token from a meeting join link")
public record ValidateInviteTokenRequest(
        @Schema(description = "The raw invite token string extracted from the join link URL")
        @NotBlank String token) {}
