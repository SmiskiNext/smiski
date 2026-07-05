package io.github.smiskinext.meetingmanagement.presentation;

import io.github.smiskinext.meetingmanagement.application.command.RespondInviteCommand;
import io.github.smiskinext.meetingmanagement.application.response.InviteeRespondResponse;
import io.github.smiskinext.meetingmanagement.application.usecase.RespondInviteUseCase;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.presentation.request.RespondInviteRequest;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.infrastructure.web.JsendResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Invitee Responses", description = "Invitation response endpoints")
public class InviteeResponseController extends BaseController {

    private final RespondInviteUseCase respondInviteUseCase;

    public InviteeResponseController(RespondInviteUseCase respondInviteUseCase) {
        this.respondInviteUseCase = respondInviteUseCase;
    }

    @Operation(summary = "Accept or decline a meeting invitation")
    @PatchMapping(value = "/{version}/meetings/{meetingId}/invitees/me", version = "1.0")
    public ResponseEntity<JsendResponse<InviteeRespondResponse>> respondInvite(
            @PathVariable UUID meetingId,
            @Valid @RequestBody RespondInviteRequest request,
            Authentication auth) {
        UUID requesterId = extractUserId(auth);
        if (requesterId == null) return unauthenticated();

        return switch (respondInviteUseCase.execute(new RespondInviteCommand(
                meetingId, requesterId, requesterId, request.response()))) {
            case Result.Success<InviteeRespondResponse, MeetingError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<InviteeRespondResponse, MeetingError> f -> errorResponse(f.error());
        };
    }
}
