package io.github.smiskinext.meetingmanagement.presentation;

import io.github.smiskinext.meetingmanagement.application.command.CancelMeetingCommand;
import io.github.smiskinext.meetingmanagement.application.command.EndMeetingCommand;
import io.github.smiskinext.meetingmanagement.application.query.GetHostMeetingsQuery;
import io.github.smiskinext.meetingmanagement.application.query.GetMeetingByShortCodeQuery;
import io.github.smiskinext.meetingmanagement.application.query.GetMeetingQuery;
import io.github.smiskinext.meetingmanagement.application.response.MeetingResponse;
import io.github.smiskinext.meetingmanagement.application.response.MeetingSettingsResponse;
import io.github.phunguy65.zms.meetingmanagement.application.usecase.*;
import io.github.smiskinext.meetingmanagement.application.usecase.*;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.presentation.request.CreateInstantMeetingRequest;
import io.github.smiskinext.meetingmanagement.presentation.request.MeetingSettingsRequest;
import io.github.smiskinext.meetingmanagement.presentation.request.ScheduleMeetingRequest;
import io.github.phunguy65.zms.shared.domain.CursorErrorCode;
import io.github.phunguy65.zms.shared.domain.CursorTokenEncoder;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.ScrollCursor;
import io.github.phunguy65.zms.shared.infrastructure.web.CursorScrollResponse;
import io.github.phunguy65.zms.shared.infrastructure.web.FailData;
import io.github.phunguy65.zms.shared.infrastructure.web.JsendResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Meetings", description = "Meeting lifecycle management")
public class MeetingController extends BaseController {

    private final ScheduleMeetingUseCase scheduleMeetingUseCase;
    private final CreateInstantMeetingUseCase createInstantMeetingUseCase;
    private final GetMeetingUseCase getMeetingUseCase;
    private final GetMeetingByShortCodeUseCase getMeetingByShortCodeUseCase;
    private final GetHostMeetingsUseCase getHostMeetingsUseCase;
    private final EndMeetingUseCase endMeetingUseCase;
    private final CancelMeetingUseCase cancelMeetingUseCase;
    private final PutMeetingSettingsUseCase putMeetingSettingsUseCase;
    private final CursorTokenEncoder cursorTokenEncoder;

    public MeetingController(
            ScheduleMeetingUseCase scheduleMeetingUseCase,
            CreateInstantMeetingUseCase createInstantMeetingUseCase,
            GetMeetingUseCase getMeetingUseCase,
            GetMeetingByShortCodeUseCase getMeetingByShortCodeUseCase,
            GetHostMeetingsUseCase getHostMeetingsUseCase,
            EndMeetingUseCase endMeetingUseCase,
            CancelMeetingUseCase cancelMeetingUseCase,
            PutMeetingSettingsUseCase putMeetingSettingsUseCase,
            CursorTokenEncoder cursorTokenEncoder) {
        this.scheduleMeetingUseCase = scheduleMeetingUseCase;
        this.createInstantMeetingUseCase = createInstantMeetingUseCase;
        this.getMeetingUseCase = getMeetingUseCase;
        this.getMeetingByShortCodeUseCase = getMeetingByShortCodeUseCase;
        this.getHostMeetingsUseCase = getHostMeetingsUseCase;
        this.endMeetingUseCase = endMeetingUseCase;
        this.cancelMeetingUseCase = cancelMeetingUseCase;
        this.putMeetingSettingsUseCase = putMeetingSettingsUseCase;
        this.cursorTokenEncoder = cursorTokenEncoder;
    }

    @Operation(summary = "Schedule a future meeting")
    @PostMapping(value = "/{version}/meetings:schedule", version = "1.0")
    public ResponseEntity<JsendResponse<MeetingResponse>> scheduleMeeting(
            @Valid @RequestBody ScheduleMeetingRequest request, Authentication auth) {
        UUID hostId = extractUserId(auth);
        if (hostId == null) return unauthenticated();
        return switch (scheduleMeetingUseCase.execute(request.toCommand(hostId))) {
            case Result.Success<MeetingResponse, MeetingError> s ->
                ResponseEntity.status(HttpStatus.CREATED).body(JsendResponse.success(s.value()));
            case Result.Failure<MeetingResponse, MeetingError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Create an instant meeting")
    @PostMapping(value = "/{version}/meetings:instant", version = "1.0")
    public ResponseEntity<JsendResponse<MeetingResponse>> createInstantMeeting(
            @Valid @RequestBody CreateInstantMeetingRequest request, Authentication auth) {
        UUID hostId = extractUserId(auth);
        if (hostId == null) return unauthenticated();
        return switch (createInstantMeetingUseCase.execute(request.toCommand(hostId))) {
            case Result.Success<MeetingResponse, MeetingError> s ->
                ResponseEntity.status(HttpStatus.CREATED).body(JsendResponse.success(s.value()));
            case Result.Failure<MeetingResponse, MeetingError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Get meeting detail by ID")
    @GetMapping(value = "/{version}/meetings/{id}", version = "1.0")
    public ResponseEntity<JsendResponse<MeetingResponse>> getMeeting(@PathVariable UUID id) {
        return switch (getMeetingUseCase.execute(new GetMeetingQuery(id))) {
            case Result.Success<MeetingResponse, MeetingError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<MeetingResponse, MeetingError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Get meeting by short code")
    @GetMapping(value = "/{version}/meetings:byShortCode", version = "1.0")
    public ResponseEntity<JsendResponse<MeetingResponse>> getMeetingByShortCode(
            @RequestParam String code) {
        return switch (getMeetingByShortCodeUseCase.execute(new GetMeetingByShortCodeQuery(code))) {
            case Result.Success<MeetingResponse, MeetingError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<MeetingResponse, MeetingError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "List host meetings with cursor pagination")
    @SuppressWarnings("unchecked")
    @GetMapping(value = "/{version}/meetings", version = "1.0")
    public ResponseEntity<JsendResponse<CursorScrollResponse<MeetingResponse>>> listHostMeetings(
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) @Nullable String pageToken,
            Authentication auth) {
        UUID hostId = extractUserId(auth);
        if (hostId == null) return unauthenticated();

        if (pageToken == null) {
            return executeGetMeetings(new GetHostMeetingsQuery(hostId, pageSize, null));
        }
        var decodeResult = cursorTokenEncoder.decode(pageToken);
        return switch (decodeResult) {
            case Result.Failure<ScrollCursor, CursorErrorCode> f ->
                (ResponseEntity<JsendResponse<CursorScrollResponse<MeetingResponse>>>)
                        (ResponseEntity<?>) ResponseEntity.badRequest()
                                .body(JsendResponse.fail(
                                        new FailData(f.error().name(), f.error(), List.of())));
            case Result.Success<ScrollCursor, CursorErrorCode> s ->
                executeGetMeetings(new GetHostMeetingsQuery(hostId, pageSize, s.value()));
        };
    }

    private ResponseEntity<JsendResponse<CursorScrollResponse<MeetingResponse>>> executeGetMeetings(
            GetHostMeetingsQuery query) {
        var pageResult = getHostMeetingsUseCase.execute(query);
        String nextPageToken = null;
        if (pageResult.hasNext() && !pageResult.items().isEmpty()) {
            var last = pageResult.items().getLast();
            nextPageToken = cursorTokenEncoder.encode(last.createdAt(), last.id());
        }
        var response =
                new CursorScrollResponse<>(pageResult.items(), query.pageSize(), nextPageToken);
        return ResponseEntity.ok(JsendResponse.success(response));
    }

    @Operation(summary = "End a meeting")
    @PostMapping(value = "/{version}/meetings/{id}:end", version = "1.0")
    public ResponseEntity<JsendResponse<Void>> endMeeting(
            @PathVariable UUID id, Authentication auth) {
        UUID requesterId = extractUserId(auth);
        if (requesterId == null) return unauthenticated();
        return switch (endMeetingUseCase.execute(new EndMeetingCommand(id, requesterId))) {
            case Result.Success<Void, MeetingError> _ -> ResponseEntity.ok(JsendResponse.success());
            case Result.Failure<Void, MeetingError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Cancel a meeting")
    @PostMapping(value = "/{version}/meetings/{id}:cancel", version = "1.0")
    public ResponseEntity<JsendResponse<Void>> cancelMeeting(
            @PathVariable UUID id, Authentication auth) {
        UUID requesterId = extractUserId(auth);
        if (requesterId == null) return unauthenticated();
        return switch (cancelMeetingUseCase.execute(new CancelMeetingCommand(id, requesterId))) {
            case Result.Success<Void, MeetingError> _ -> ResponseEntity.ok(JsendResponse.success());
            case Result.Failure<Void, MeetingError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Replace meeting settings")
    @PutMapping(value = "/{version}/meetings/{id}/settings", version = "1.0")
    public ResponseEntity<JsendResponse<MeetingSettingsResponse>> putMeetingSettings(
            @PathVariable UUID id,
            @Valid @RequestBody MeetingSettingsRequest request,
            Authentication auth) {
        UUID requesterId = extractUserId(auth);
        if (requesterId == null) return unauthenticated();
        return switch (putMeetingSettingsUseCase.execute(request.toCommand(id, requesterId))) {
            case Result.Success<MeetingSettingsResponse, MeetingError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<MeetingSettingsResponse, MeetingError> f ->
                errorResponse(f.error());
        };
    }
}
