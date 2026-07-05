package io.github.smiskinext.meetingmanagement.presentation;

import io.github.smiskinext.meetingmanagement.application.command.ApproveAllJoinRequestsCommand;
import io.github.smiskinext.meetingmanagement.application.command.ApproveJoinRequestCommand;
import io.github.smiskinext.meetingmanagement.application.command.DenyJoinRequestCommand;
import io.github.smiskinext.meetingmanagement.application.query.GetJoinRequestsQuery;
import io.github.smiskinext.meetingmanagement.application.response.ApproveAllResponse;
import io.github.smiskinext.meetingmanagement.application.response.JoinRequestResponse;
import io.github.smiskinext.meetingmanagement.application.response.RequestJoinResponse;
import io.github.phunguy65.zms.meetingmanagement.application.usecase.*;
import io.github.smiskinext.meetingmanagement.application.usecase.*;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestStatus;
import io.github.smiskinext.meetingmanagement.infrastructure.sse.MeetingSseManager;
import io.github.smiskinext.meetingmanagement.presentation.request.JoinRequestRequest;
import io.github.smiskinext.shared.domain.OffsetPageResponse;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.infrastructure.web.JsendResponse;
import io.github.smiskinext.shared.infrastructure.web.OffsetScrollResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Tag(name = "Join Requests", description = "Meeting join request management")
public class JoinRequestController extends BaseController {

    private final RequestJoinUseCase requestJoinUseCase;
    private final ApproveJoinRequestUseCase approveJoinRequestUseCase;
    private final DenyJoinRequestUseCase denyJoinRequestUseCase;
    private final ApproveAllJoinRequestsUseCase approveAllJoinRequestsUseCase;
    private final GetJoinRequestsUseCase getJoinRequestsUseCase;
    private final MeetingSseManager meetingSseManager;

    public JoinRequestController(
            RequestJoinUseCase requestJoinUseCase,
            ApproveJoinRequestUseCase approveJoinRequestUseCase,
            DenyJoinRequestUseCase denyJoinRequestUseCase,
            ApproveAllJoinRequestsUseCase approveAllJoinRequestsUseCase,
            GetJoinRequestsUseCase getJoinRequestsUseCase,
            MeetingSseManager meetingSseManager) {
        this.requestJoinUseCase = requestJoinUseCase;
        this.approveJoinRequestUseCase = approveJoinRequestUseCase;
        this.denyJoinRequestUseCase = denyJoinRequestUseCase;
        this.approveAllJoinRequestsUseCase = approveAllJoinRequestsUseCase;
        this.getJoinRequestsUseCase = getJoinRequestsUseCase;
        this.meetingSseManager = meetingSseManager;
    }

    @Operation(summary = "Request to join a meeting")
    @PostMapping(value = "/{version}/meetings/{id}:requestJoin", version = "1.0")
    public ResponseEntity<JsendResponse<RequestJoinResponse>> requestJoin(
            @PathVariable UUID id,
            @Valid @RequestBody JoinRequestRequest request,
            Authentication auth) {
        UUID userId = extractUserId(auth);
        return switch (requestJoinUseCase.execute(request.toCommand(id, userId))) {
            case Result.Success<RequestJoinResponse, MeetingError> s -> {
                int httpStatus = s.value().status() == JoinRequestStatus.PENDING ? 202 : 200;
                yield ResponseEntity.status(httpStatus).body(JsendResponse.success(s.value()));
            }
            case Result.Failure<RequestJoinResponse, MeetingError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "List join requests for a meeting")
    @GetMapping(value = "/{version}/meetings/{id}/joinRequests", version = "1.0")
    public ResponseEntity<JsendResponse<OffsetScrollResponse<JoinRequestResponse>>>
            listJoinRequests(
                    @PathVariable UUID id,
                    @RequestParam(defaultValue = "20") int pageSize,
                    @RequestParam(defaultValue = "0") int offset,
                    Authentication auth) {
        UUID requesterId = extractUserId(auth);
        if (requesterId == null) return unauthenticated();
        return switch (getJoinRequestsUseCase.execute(
                new GetJoinRequestsQuery(id, requesterId, pageSize, offset))) {
            case Result.Success<OffsetPageResponse<JoinRequestResponse>, MeetingError> s ->
                ResponseEntity.ok(JsendResponse.success(toOffsetResponse(s.value())));
            case Result.Failure<OffsetPageResponse<JoinRequestResponse>, MeetingError> f ->
                errorResponse(f.error());
        };
    }

    private <T> OffsetScrollResponse<T> toOffsetResponse(OffsetPageResponse<T> page) {
        Integer nextOffset = page.hasNext() ? page.offset() + page.pageSize() : null;
        return new OffsetScrollResponse<>(page.items(), page.pageSize(), nextOffset);
    }

    @Operation(summary = "Approve a join request")
    @PostMapping(
            value = "/{version}/meetings/{id}/joinRequests/{requestId}:approve",
            version = "1.0")
    public ResponseEntity<JsendResponse<String>> approveJoinRequest(
            @PathVariable UUID id, @PathVariable UUID requestId, Authentication auth) {
        UUID approvedBy = extractUserId(auth);
        if (approvedBy == null) return unauthenticated();
        return switch (approveJoinRequestUseCase.execute(
                new ApproveJoinRequestCommand(id, requestId, approvedBy))) {
            case Result.Success<String, MeetingError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<String, MeetingError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Deny a join request")
    @PostMapping(value = "/{version}/meetings/{id}/joinRequests/{requestId}:deny", version = "1.0")
    public ResponseEntity<JsendResponse<Void>> denyJoinRequest(
            @PathVariable UUID id, @PathVariable UUID requestId, Authentication auth) {
        UUID deniedBy = extractUserId(auth);
        if (deniedBy == null) return unauthenticated();
        return switch (denyJoinRequestUseCase.execute(
                new DenyJoinRequestCommand(id, requestId, deniedBy))) {
            case Result.Success<Void, MeetingError> _ -> ResponseEntity.ok(JsendResponse.success());
            case Result.Failure<Void, MeetingError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Approve all pending join requests")
    @PostMapping(value = "/{version}/meetings/{id}/joinRequests:approveAll", version = "1.0")
    public ResponseEntity<JsendResponse<ApproveAllResponse>> approveAllJoinRequests(
            @PathVariable UUID id, Authentication auth) {
        UUID approvedBy = extractUserId(auth);
        if (approvedBy == null) return unauthenticated();
        return switch (approveAllJoinRequestsUseCase.execute(
                new ApproveAllJoinRequestsCommand(id, approvedBy))) {
            case Result.Success<ApproveAllResponse, MeetingError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<ApproveAllResponse, MeetingError> f -> errorResponse(f.error());
        };
    }

    /**
     * GET /v1.0/joinRequests/{requestId}/events — SSE stream for guest awaiting join request
     * resolution (no auth required).
     */
    @Operation(summary = "Subscribe to join request events (SSE)")
    @GetMapping(
            value = "/{version}/joinRequests/{requestId}/events",
            version = "1.0",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribeGuestToEvents(@PathVariable UUID requestId) {
        SseEmitter emitter = meetingSseManager.subscribeGuest(requestId);
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(emitter);
    }

    /**
     * GET /v1.0/meetings/{id}/events — SSE stream for host (requires auth).
     */
    @Operation(summary = "Subscribe to meeting events (SSE, host only)")
    @GetMapping(
            value = "/{version}/meetings/{id}/events",
            version = "1.0",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribeToEvents(
            @PathVariable UUID id, Authentication auth) {
        UUID userId = extractUserId(auth);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        SseEmitter emitter = meetingSseManager.subscribeHost(id, userId);
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(emitter);
    }
}
