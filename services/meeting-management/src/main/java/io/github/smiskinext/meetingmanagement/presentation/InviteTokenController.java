package io.github.smiskinext.meetingmanagement.presentation;

import io.github.smiskinext.meetingmanagement.application.response.ValidateInviteTokenResponse;
import io.github.smiskinext.meetingmanagement.application.usecase.ValidateInviteTokenUseCase;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.presentation.request.ValidateInviteTokenRequest;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.infrastructure.web.JsendResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoints for invite token operations.
 *
 * <p>No authentication is required for these endpoints — the token itself acts as the
 * authorization proof.
 *
 * <p>The token validation endpoint ({@code POST .../invite-tokens:validate}) marks the token as
 * USED in the database. Subsequent validation attempts for the same token will fail with
 * {@code INVITE_TOKEN_INVALID}.
 */
@RestController
@Tag(name = "Invite Tokens", description = "Invite token validation (public, no auth required)")
public class InviteTokenController extends BaseController {

    private final ValidateInviteTokenUseCase validateInviteTokenUseCase;

    public InviteTokenController(ValidateInviteTokenUseCase validateInviteTokenUseCase) {
        this.validateInviteTokenUseCase = validateInviteTokenUseCase;
    }

    @Operation(
            summary = "Validate an invite token",
            description = "Validates a raw invite token from the meeting invite link. "
                    + "On success the token is marked as USED and cannot be reused. "
                    + "Returns meeting details needed to join.")
    @PostMapping(value = "/{version}/meetings/invite-tokens:validate", version = "1.0")
    public ResponseEntity<JsendResponse<ValidateInviteTokenResponse>> validateToken(
            @Valid @RequestBody ValidateInviteTokenRequest request) {
        return switch (validateInviteTokenUseCase.execute(request.token())) {
            case Result.Success<ValidateInviteTokenResponse, MeetingError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<ValidateInviteTokenResponse, MeetingError> f ->
                errorResponse(f.error());
        };
    }
}
