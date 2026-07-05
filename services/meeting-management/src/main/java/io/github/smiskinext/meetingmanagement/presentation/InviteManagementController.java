package io.github.smiskinext.meetingmanagement.presentation;

import io.github.smiskinext.meetingmanagement.application.command.ResendInviteCommand;
import io.github.smiskinext.meetingmanagement.application.response.InviteeListResponse;
import io.github.smiskinext.meetingmanagement.application.usecase.AddInviteeUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.GetInviteesUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.ResendInviteUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.RevokeInviteUseCase;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.presentation.request.AddInviteeRequest;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.infrastructure.web.JsendResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Host-facing endpoints for managing meeting invitees and their invite tokens.
 *
 * <p>All endpoints require authentication (the host must be the meeting owner).
 * The public token validation endpoint lives in {@link InviteTokenController}.
 */
@RestController
@Tag(name = "Invite Management", description = "Manage meeting invitees and invite tokens")
public class InviteManagementController extends BaseController {

    private final GetInviteesUseCase getInviteesUseCase;
    private final AddInviteeUseCase addInviteeUseCase;
    private final ResendInviteUseCase resendInviteUseCase;
    private final RevokeInviteUseCase revokeInviteUseCase;

    public InviteManagementController(
            GetInviteesUseCase getInviteesUseCase,
            AddInviteeUseCase addInviteeUseCase,
            ResendInviteUseCase resendInviteUseCase,
            RevokeInviteUseCase revokeInviteUseCase) {
        this.getInviteesUseCase = getInviteesUseCase;
        this.addInviteeUseCase = addInviteeUseCase;
        this.resendInviteUseCase = resendInviteUseCase;
        this.revokeInviteUseCase = revokeInviteUseCase;
    }

    @Operation(summary = "List all invitees for a meeting")
    @GetMapping(value = "/{version}/meetings/{meetingId}/invitees", version = "1.0")
    public ResponseEntity<JsendResponse<List<InviteeListResponse>>> getInvitees(
            @PathVariable UUID meetingId, Authentication auth) {
        UUID requesterId = extractUserId(auth);
        if (requesterId == null) {
            return unauthenticated();
        }
        return switch (getInviteesUseCase.execute(meetingId)) {
            case Result.Success<List<InviteeListResponse>, MeetingError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<List<InviteeListResponse>, MeetingError> f ->
                errorResponse(f.error());
        };
    }

    @Operation(summary = "Add a new invitee to a meeting")
    @PostMapping(value = "/{version}/meetings/{meetingId}/invitees", version = "1.0")
    public ResponseEntity<JsendResponse<InviteeListResponse>> addInvitee(
            @PathVariable UUID meetingId,
            @Valid @RequestBody AddInviteeRequest request,
            Authentication auth) {
        UUID requesterId = extractUserId(auth);
        if (requesterId == null) {
            return unauthenticated();
        }
        return switch (addInviteeUseCase.execute(request.toCommand(meetingId, requesterId))) {
            case Result.Success<InviteeListResponse, MeetingError> s -> {
                yield ResponseEntity.status(HttpStatus.CREATED)
                        .body(JsendResponse.success(s.value()));
            }
            case Result.Failure<InviteeListResponse, MeetingError> f -> errorResponse(f.error());
        };
    }

    @Operation(
            summary = "Resend an invite to an existing invitee (revokes old token, issues new one)")
    @PostMapping(
            value = "/{version}/meetings/{meetingId}/invitees/{inviteeId}/resend",
            version = "1.0")
    public ResponseEntity<JsendResponse<InviteeListResponse>> resendInvite(
            @PathVariable UUID meetingId, @PathVariable UUID inviteeId, Authentication auth) {
        UUID requesterId = extractUserId(auth);
        if (requesterId == null) {
            return unauthenticated();
        }
        var command = new ResendInviteCommand(meetingId, inviteeId, requesterId);
        return switch (resendInviteUseCase.execute(command)) {
            case Result.Success<InviteeListResponse, MeetingError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<InviteeListResponse, MeetingError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Revoke an invitee and their pending invite token")
    @DeleteMapping(value = "/{version}/meetings/{meetingId}/invitees/{inviteeId}", version = "1.0")
    public ResponseEntity<JsendResponse<InviteeListResponse>> revokeInvite(
            @PathVariable UUID meetingId, @PathVariable UUID inviteeId, Authentication auth) {
        UUID requesterId = extractUserId(auth);
        if (requesterId == null) {
            return unauthenticated();
        }
        return switch (revokeInviteUseCase.execute(meetingId, inviteeId, requesterId)) {
            case Result.Success<InviteeListResponse, MeetingError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<InviteeListResponse, MeetingError> f -> errorResponse(f.error());
        };
    }
}
