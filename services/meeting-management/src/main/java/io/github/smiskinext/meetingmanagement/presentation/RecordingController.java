package io.github.smiskinext.meetingmanagement.presentation;

import io.github.smiskinext.meetingmanagement.application.command.StartRecordingCommand;
import io.github.smiskinext.meetingmanagement.application.command.StopRecordingCommand;
import io.github.smiskinext.meetingmanagement.application.query.GetMeetingRecordingsQuery;
import io.github.smiskinext.meetingmanagement.application.query.GetRecordingQuery;
import io.github.smiskinext.meetingmanagement.application.response.RecordingResponse;
import io.github.phunguy65.zms.meetingmanagement.application.usecase.*;
import io.github.smiskinext.meetingmanagement.application.usecase.*;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.presentation.request.CompleteRecordingRequest;
import io.github.phunguy65.zms.shared.domain.CursorErrorCode;
import io.github.phunguy65.zms.shared.domain.CursorTokenEncoder;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.ScrollCursor;
import io.github.phunguy65.zms.shared.infrastructure.web.CursorScrollResponse;
import io.github.phunguy65.zms.shared.infrastructure.web.FailData;
import io.github.phunguy65.zms.shared.infrastructure.web.JsendResponse;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.RecordingId;
import io.swagger.v3.oas.annotations.Hidden;
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
@Tag(name = "Recordings", description = "Meeting recording management")
public class RecordingController extends BaseController {

    private final StartRecordingUseCase startRecordingUseCase;
    private final StopRecordingUseCase stopRecordingUseCase;
    private final CompleteRecordingUseCase completeRecordingUseCase;
    private final GetRecordingUseCase getRecordingUseCase;
    private final GetMeetingRecordingsUseCase getMeetingRecordingsUseCase;
    private final CursorTokenEncoder cursorTokenEncoder;

    public RecordingController(
            StartRecordingUseCase startRecordingUseCase,
            StopRecordingUseCase stopRecordingUseCase,
            CompleteRecordingUseCase completeRecordingUseCase,
            GetRecordingUseCase getRecordingUseCase,
            GetMeetingRecordingsUseCase getMeetingRecordingsUseCase,
            CursorTokenEncoder cursorTokenEncoder) {
        this.startRecordingUseCase = startRecordingUseCase;
        this.stopRecordingUseCase = stopRecordingUseCase;
        this.completeRecordingUseCase = completeRecordingUseCase;
        this.getRecordingUseCase = getRecordingUseCase;
        this.getMeetingRecordingsUseCase = getMeetingRecordingsUseCase;
        this.cursorTokenEncoder = cursorTokenEncoder;
    }

    @Operation(summary = "Start recording a meeting")
    @PostMapping(value = "/{version}/meetings/{id}/recordings:start", version = "1.0")
    public ResponseEntity<JsendResponse<RecordingResponse>> startRecording(
            @PathVariable UUID id, Authentication auth) {
        UUID requesterId = extractUserId(auth);
        if (requesterId == null) return unauthenticated();
        return switch (startRecordingUseCase.execute(new StartRecordingCommand(id, requesterId))) {
            case Result.Success<RecordingResponse, MeetingError> s ->
                ResponseEntity.status(HttpStatus.CREATED).body(JsendResponse.success(s.value()));
            case Result.Failure<RecordingResponse, MeetingError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Stop recording a meeting")
    @PostMapping(value = "/{version}/meetings/{id}/recordings:stop", version = "1.0")
    public ResponseEntity<JsendResponse<Void>> stopRecording(
            @PathVariable UUID id, Authentication auth) {
        UUID requesterId = extractUserId(auth);
        if (requesterId == null) return unauthenticated();
        return switch (stopRecordingUseCase.execute(new StopRecordingCommand(id, requesterId))) {
            case Result.Success<Void, MeetingError> _ -> ResponseEntity.ok(JsendResponse.success());
            case Result.Failure<Void, MeetingError> f -> errorResponse(f.error());
        };
    }

    @Hidden
    @PostMapping(value = "/{version}/recordings/{id}:complete", version = "1.0")
    public ResponseEntity<JsendResponse<Void>> completeRecording(
            @PathVariable UUID id, @Valid @RequestBody CompleteRecordingRequest request) {
        return switch (completeRecordingUseCase.execute(request.toCommand(id))) {
            case Result.Success<Void, MeetingError> _ -> ResponseEntity.ok(JsendResponse.success());
            case Result.Failure<Void, MeetingError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Get recording by ID")
    @GetMapping(value = "/{version}/recordings/{id}", version = "1.0")
    public ResponseEntity<JsendResponse<RecordingResponse>> getRecording(@PathVariable UUID id) {
        return switch (getRecordingUseCase.execute(new GetRecordingQuery(
                RecordingId.of(
                        id)))) {
            case Result.Success<RecordingResponse, MeetingError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<RecordingResponse, MeetingError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "List recordings for a meeting")
    @SuppressWarnings("unchecked")
    @GetMapping(value = "/{version}/meetings/{id}/recordings", version = "1.0")
    public ResponseEntity<JsendResponse<CursorScrollResponse<RecordingResponse>>>
            listMeetingRecordings(
                    @PathVariable UUID id,
                    @RequestParam(defaultValue = "20") int pageSize,
                    @RequestParam(required = false) @Nullable String pageToken) {
        if (pageToken == null) {
            return executeGetRecordings(new GetMeetingRecordingsQuery(id, pageSize, null));
        }
        var decodeResult = cursorTokenEncoder.decode(pageToken);
        return switch (decodeResult) {
            case Result.Failure<ScrollCursor, CursorErrorCode> f ->
                (ResponseEntity<JsendResponse<CursorScrollResponse<RecordingResponse>>>)
                        (ResponseEntity<?>) ResponseEntity.badRequest()
                                .body(JsendResponse.fail(
                                        new FailData(f.error().name(), f.error(), List.of())));
            case Result.Success<ScrollCursor, CursorErrorCode> s ->
                executeGetRecordings(new GetMeetingRecordingsQuery(id, pageSize, s.value()));
        };
    }

    private ResponseEntity<JsendResponse<CursorScrollResponse<RecordingResponse>>>
            executeGetRecordings(GetMeetingRecordingsQuery query) {
        var pageResult = getMeetingRecordingsUseCase.execute(query);
        String nextPageToken = null;
        if (pageResult.hasNext() && !pageResult.items().isEmpty()) {
            var last = pageResult.items().getLast();
            nextPageToken = cursorTokenEncoder.encode(last.createdAt(), last.id());
        }
        var response =
                new CursorScrollResponse<>(pageResult.items(), query.pageSize(), nextPageToken);
        return ResponseEntity.ok(JsendResponse.success(response));
    }
}
