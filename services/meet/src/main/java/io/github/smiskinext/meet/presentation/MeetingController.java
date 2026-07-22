package io.github.smiskinext.meet.presentation;

import io.github.smiskinext.meet.application.command.CreateInstantMeetingCommand;
import io.github.smiskinext.meet.application.command.ScheduleMeetingCommand;
import io.github.smiskinext.meet.application.query.GetMeetingQuery;
import io.github.smiskinext.meet.application.result.BatchDeleteMeetingsResult;
import io.github.smiskinext.meet.application.result.CreateInstantMeetingResult;
import io.github.smiskinext.meet.application.result.DeleteMeetingResult;
import io.github.smiskinext.meet.application.result.GetMeetingResult;
import io.github.smiskinext.meet.application.result.ListMeetingsResult;
import io.github.smiskinext.meet.application.result.ScheduleMeetingResult;
import io.github.smiskinext.meet.application.result.UpdateMeetingInviteesResult;
import io.github.smiskinext.meet.application.result.UpdateMeetingResult;
import io.github.smiskinext.meet.application.usecase.BatchDeleteMeetingsUseCase;
import io.github.smiskinext.meet.application.usecase.CreateInstantMeetingUseCase;
import io.github.smiskinext.meet.application.usecase.DeleteMeetingUseCase;
import io.github.smiskinext.meet.application.usecase.GetMeetingUseCase;
import io.github.smiskinext.meet.application.usecase.ListMeetingsUseCase;
import io.github.smiskinext.meet.application.usecase.ScheduleMeetingUseCase;
import io.github.smiskinext.meet.application.usecase.UpdateMeetingInviteesUseCase;
import io.github.smiskinext.meet.application.usecase.UpdateMeetingUseCase;
import io.github.smiskinext.meet.domain.ListMeetingsError;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.presentation.request.BatchDeleteMeetingsRequest;
import io.github.smiskinext.meet.presentation.request.CreateInstantMeetingRequest;
import io.github.smiskinext.meet.presentation.request.ListMeetingsRequest;
import io.github.smiskinext.meet.presentation.request.ScheduleMeetingRequest;
import io.github.smiskinext.meet.presentation.request.UpdateMeetingInviteesRequest;
import io.github.smiskinext.meet.presentation.request.UpdateMeetingRequest;
import io.github.smiskinext.meet.presentation.response.BatchDeleteMeetingsResponse;
import io.github.smiskinext.meet.presentation.response.CreateInstantMeetingResponse;
import io.github.smiskinext.meet.presentation.response.DeleteMeetingResponse;
import io.github.smiskinext.meet.presentation.response.GetMeetingResponse;
import io.github.smiskinext.meet.presentation.response.MeetingListPageResponse;
import io.github.smiskinext.meet.presentation.response.MeetingSummaryResponse;
import io.github.smiskinext.meet.presentation.response.ScheduleMeetingResponse;
import io.github.smiskinext.meet.presentation.response.UpdateMeetingInviteesResponse;
import io.github.smiskinext.meet.presentation.response.UpdateMeetingResponse;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.infrastructure.identity.AccountContext;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import io.github.smiskinext.shared.infrastructure.web.PageResponse;
import io.github.smiskinext.shared.infrastructure.web.ProblemDetailSchema;
import io.github.smiskinext.shared.infrastructure.web.ResultResponder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@Tag(
        name = "meeting-controller",
        description =
                "Meeting lifecycle: schedule, create instant, update, list, delete meetings and manage invitees")
public class MeetingController {

    private final CreateInstantMeetingUseCase createInstantMeetingUseCase;
    private final ScheduleMeetingUseCase scheduleMeetingUseCase;
    private final UpdateMeetingUseCase updateMeetingUseCase;
    private final UpdateMeetingInviteesUseCase updateMeetingInviteesUseCase;
    private final ListMeetingsUseCase listMeetingsUseCase;
    private final GetMeetingUseCase getMeetingUseCase;
    private final DeleteMeetingUseCase deleteMeetingUseCase;
    private final BatchDeleteMeetingsUseCase batchDeleteMeetingsUseCase;
    private final ResultResponder responder;

    public MeetingController(
            CreateInstantMeetingUseCase createInstantMeetingUseCase,
            ScheduleMeetingUseCase scheduleMeetingUseCase,
            UpdateMeetingUseCase updateMeetingUseCase,
            UpdateMeetingInviteesUseCase updateMeetingInviteesUseCase,
            ListMeetingsUseCase listMeetingsUseCase,
            GetMeetingUseCase getMeetingUseCase,
            DeleteMeetingUseCase deleteMeetingUseCase,
            BatchDeleteMeetingsUseCase batchDeleteMeetingsUseCase,
            ResultResponder responder) {
        this.createInstantMeetingUseCase = createInstantMeetingUseCase;
        this.scheduleMeetingUseCase = scheduleMeetingUseCase;
        this.updateMeetingUseCase = updateMeetingUseCase;
        this.updateMeetingInviteesUseCase = updateMeetingInviteesUseCase;
        this.listMeetingsUseCase = listMeetingsUseCase;
        this.getMeetingUseCase = getMeetingUseCase;
        this.deleteMeetingUseCase = deleteMeetingUseCase;
        this.batchDeleteMeetingsUseCase = batchDeleteMeetingsUseCase;
        this.responder = responder;
    }

    @Operation(
            summary = "List tenant meetings",
            description = "Lists meetings in the caller's tenant with optional creator, status, "
                    + "issue, and text filters, two sort modes, and opaque keyset pagination. "
                    + "The request body is optional; an empty body lists with defaults.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Page of tenant meetings",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MeetingListPageResponse.class),
                                examples = @ExampleObject(name = "page", value = """
                        {
                          "data": [
                            {
                              "id": "0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90",
                              "hostId": "account-123",
                              "shortCode": "abc-defg-hij",
                              "title": "Sprint planning",
                              "description": "Plan the next sprint",
                              "issueKey": "SMISKI-102",
                              "type": "SCHEDULED",
                              "status": "SCHEDULED",
                              "startTime": "2025-02-01T14:00:00Z",
                              "endTime": "2025-02-01T15:00:00Z",
                              "createdAt": "2025-01-15T10:30:00Z",
                              "settings": {
                                "admissionPolicy": "MANUAL_APPROVAL",
                                "maxParticipants": 50,
                                "allowScreenShare": true,
                                "chatEnabled": true,
                                "allowMicrophone": true,
                                "allowVideo": true
                              }
                            }
                          ],
                          "meta": {"size": 1, "hasNext": true, "nextPageToken": "Uy5leUov..."}
                        }"""))),
        @ApiResponse(
                responseCode = "400",
                description = "Missing account header, page-size validation, or invalid cursor",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = {
                                    @ExampleObject(
                                            name = "invalidCursor",
                                            summary = "Invalid or sort-mismatched page token",
                                            value = """
                            {
                              "type": "about:blank",
                              "title": "Invalid page cursor",
                              "status": 400,
                              "detail": "The page cursor is invalid; restart from the first page.",
                              "code": "INVALID_CURSOR",
                              "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                            }"""),
                                    @ExampleObject(
                                            name = "missingAccount",
                                            summary = "Missing X-Account-Id header",
                                            value = """
                            {
                              "type": "about:blank",
                              "title": "Bad Request",
                              "status": 400,
                              "detail": "X-Account-Id header is required",
                              "code": "VALIDATION_ERROR",
                              "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                            }""")
                                }))
    })
    @PostMapping("/meetings")
    public ResponseEntity<Object> list(
            @Valid @RequestBody(required = false) ListMeetingsRequest request) {
        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return ResponseEntity.badRequest()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                    .body(org.springframework.http.ProblemDetail.forStatusAndDetail(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "X-Account-Id header is required"));
        }

        ListMeetingsRequest effectiveRequest = request != null
                ? request
                : new ListMeetingsRequest(null, null, null, null, null, null, null);
        String tenantId = TenantContext.getCurrentTenant();

        Result<ListMeetingsResult, ListMeetingsError> result =
                listMeetingsUseCase.execute(effectiveRequest.toQuery(tenantId, accountId));

        return responder.ok(result.map(MeetingController::toPage));
    }

    private static PageResponse<MeetingSummaryResponse> toPage(ListMeetingsResult result) {
        List<MeetingSummaryResponse> items =
                result.items().stream().map(MeetingSummaryResponse::from).toList();
        return PageResponse.cursor(items, result.nextPageToken());
    }

    @Operation(
            summary = "Get a meeting with its people",
            description =
                    "Returns a single meeting in the caller's tenant together with its active "
                            + "invitee list and its distinct joined-participant list. Any authenticated "
                            + "tenant member may read the meeting; access is not restricted to the host.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Meeting detail with invitees and participants",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = GetMeetingResponse.class),
                                examples = @ExampleObject(name = "detail", value = """
                        {
                          "meeting": {
                            "id": "0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90",
                            "hostId": "account-123",
                            "shortCode": "abc-defg-hij",
                            "type": "SCHEDULED",
                            "status": "SCHEDULED",
                            "title": "Sprint planning",
                            "description": "Plan the next sprint",
                            "issueLink": {
                              "issueId": "10001",
                              "issueKey": "PROJ-1",
                              "projectKey": "PROJ"
                            },
                            "settings": {
                              "admissionPolicy": "MANUAL_APPROVAL",
                              "maxParticipants": 50,
                              "allowScreenShare": true,
                              "chatEnabled": true,
                              "allowMicrophone": true,
                              "allowVideo": true
                            },
                            "startTime": "2025-02-01T14:00:00Z",
                            "endTime": "2025-02-01T15:00:00Z",
                            "zoneId": "Asia/Ho_Chi_Minh",
                            "organizerEmail": "host@example.com",
                            "organizerDisplayName": "Host User",
                            "calendarUid": "meeting-0195e0c2@smiski.app",
                            "calendarSequence": 1,
                            "createdAt": "2025-01-15T10:30:00Z"
                          },
                          "invitees": [
                            {
                              "accountId": "account-456",
                              "email": "alice@example.com",
                              "displayName": "Alice Nguyen",
                              "status": "ACCEPTED",
                              "invitedAt": "2025-01-15T10:35:00Z",
                              "respondedAt": "2025-01-15T11:00:00Z"
                            }
                          ],
                          "participants": [
                            {
                              "accountId": "account-456",
                              "displayName": "Alice Nguyen",
                              "role": "PARTICIPANT",
                              "joinedAt": "2025-02-01T14:01:00Z",
                              "leftAt": null
                            }
                          ]
                        }"""))),
        @ApiResponse(
                responseCode = "400",
                description = "Missing account header",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "missingAccount", value = """
                        {
                          "type": "about:blank",
                          "title": "Bad Request",
                          "status": 400,
                          "detail": "X-Account-Id header is required",
                          "code": "VALIDATION_ERROR",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(
                responseCode = "404",
                description = "Meeting not found, soft-deleted, or in another tenant",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notFound", value = """
                        {
                          "type": "about:blank",
                          "title": "Meeting not found",
                          "status": 404,
                          "detail": "No meeting matches the given identifier.",
                          "code": "MEETING_NOT_FOUND",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }""")))
    })
    @GetMapping("/meetings/{id}")
    public ResponseEntity<Object> get(@PathVariable UUID id) {
        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return ResponseEntity.badRequest()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                    .body(org.springframework.http.ProblemDetail.forStatusAndDetail(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "X-Account-Id header is required"));
        }
        Result<GetMeetingResult, MeetingError> result = getMeetingUseCase.execute(
                new GetMeetingQuery(id, TenantContext.getCurrentTenant(), accountId));
        return responder.ok(result.map(GetMeetingResponse::from));
    }

    @Operation(
            summary = "Update a meeting",
            description = "Updates a meeting as its host. "
                    + "Information and settings are mutable while scheduled or running; scheduled details "
                    + "are mutable only while scheduled.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Meeting updated",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = UpdateMeetingResponse.class),
                                examples = @ExampleObject(name = "updated", value = """
                        {
                          "meeting": {
                            "id": "0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90",
                            "hostId": "account-123",
                            "shortCode": "abc-defg-hij",
                            "type": "SCHEDULED",
                            "status": "SCHEDULED",
                            "title": "Sprint planning",
                            "description": "Plan the next sprint",
                            "issueLink": {
                              "issueId": "10001",
                              "issueKey": "PROJ-1",
                              "projectKey": "PROJ"
                            },
                            "settings": {
                              "admissionPolicy": "ALLOW_ALL",
                              "maxParticipants": 50,
                              "allowScreenShare": true,
                              "chatEnabled": true,
                              "allowMicrophone": true,
                              "allowVideo": true
                            },
                            "startTime": "2025-02-01T14:00:00Z",
                            "endTime": "2025-02-01T15:00:00Z",
                            "zoneId": "Asia/Ho_Chi_Minh",
                            "organizerEmail": "host@example.com",
                            "organizerDisplayName": "Host User",
                            "calendarUid": "meeting-0195e0c2@smiski.app",
                            "calendarSequence": 1,
                            "createdAt": "2025-01-15T10:30:00Z"
                          }
                        }"""))),
        @ApiResponse(
                responseCode = "400",
                description = "Validation error or missing account",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = {
                                    @ExampleObject(
                                            name = "validationError",
                                            summary = "Validation failure",
                                            value = """
                            {
                              "type": "about:blank",
                              "title": "Bad Request",
                              "status": 400,
                              "detail": "The request body failed validation",
                              "code": "VALIDATION_ERROR",
                              "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a",
                              "errors": [
                                {"field": "title", "code": "REQUIRED", "message": "must not be blank"}
                              ]
                            }"""),
                                    @ExampleObject(
                                            name = "missingAccount",
                                            summary = "Missing X-Account-Id header",
                                            value = """
                            {
                              "type": "about:blank",
                              "title": "Bad Request",
                              "status": 400,
                              "detail": "X-Account-Id header is required",
                              "code": "VALIDATION_ERROR",
                              "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                            }""")
                                })),
        @ApiResponse(
                responseCode = "403",
                description = "Only the host may update the meeting",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notOwner", value = """
                        {
                          "type": "about:blank",
                          "title": "Forbidden",
                          "status": 403,
                          "detail": "Only the host may update the meeting",
                          "code": "NOT_OWNER",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(
                responseCode = "404",
                description = "Meeting not found",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notFound", value = """
                        {
                          "type": "about:blank",
                          "title": "Not Found",
                          "status": 404,
                          "detail": "Meeting not found",
                          "code": "MEETING_NOT_FOUND",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }""")))
    })
    @PutMapping("/meetings/{id}")
    public ResponseEntity<Object> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateMeetingRequest request) {
        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return ResponseEntity.badRequest()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                    .body(org.springframework.http.ProblemDetail.forStatusAndDetail(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "X-Account-Id header is required"));
        }
        Result<UpdateMeetingResult, MeetingError> result = updateMeetingUseCase.execute(
                request.toCommand(id, accountId, TenantContext.getCurrentTenant()));
        return responder.ok(result.map(UpdateMeetingResponse::from));
    }

    @Operation(
            summary = "Replace a meeting's invitee list",
            description = "Replaces the full invitee list of a SCHEDULED meeting as its host. "
                    + "Invitees are matched by accountId: new entries are created, existing entries "
                    + "have their display name updated, and absent entries are removed.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Invitees synchronized",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @Schema(
                                                implementation =
                                                        UpdateMeetingInviteesResponse.class),
                                examples = @ExampleObject(name = "synchronized", value = """
                        {
                          "invitees": [
                            {
                              "id": "0195e0c2-8f3a-7c21-b9d4-3a2b1c4d5e60",
                              "accountId": "account-456",
                              "email": "alice@example.com",
                              "displayName": "Alice Nguyen",
                              "role": "PARTICIPANT",
                              "status": "PENDING",
                              "invitedAt": "2025-01-15T10:35:00Z",
                              "respondedAt": null
                            },
                            {
                              "id": "0195e0c2-8f3a-7c21-b9d4-4b3c2d5e6f70",
                              "accountId": "account-789",
                              "email": "bob@example.com",
                              "displayName": "Bob Tran",
                              "role": "PARTICIPANT",
                              "status": "ACCEPTED",
                              "invitedAt": "2025-01-15T10:35:00Z",
                              "respondedAt": "2025-01-15T11:00:00Z"
                            }
                          ]
                        }"""))),
        @ApiResponse(
                responseCode = "400",
                description = "Validation error or missing account",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = {
                                    @ExampleObject(
                                            name = "validationError",
                                            summary = "Validation failure",
                                            value = """
                            {
                              "type": "about:blank",
                              "title": "Bad Request",
                              "status": 400,
                              "detail": "The request body failed validation",
                              "code": "VALIDATION_ERROR",
                              "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a",
                              "errors": [
                                {"field": "invitees[0].email", "code": "INVALID_FORMAT", "message": "must be a well-formed email address"}
                              ]
                            }"""),
                                    @ExampleObject(
                                            name = "missingAccount",
                                            summary = "Missing X-Account-Id header",
                                            value = """
                            {
                              "type": "about:blank",
                              "title": "Bad Request",
                              "status": 400,
                              "detail": "X-Account-Id header is required",
                              "code": "VALIDATION_ERROR",
                              "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                            }""")
                                })),
        @ApiResponse(
                responseCode = "403",
                description = "Only the host may modify invitees",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notOwner", value = """
                        {
                          "type": "about:blank",
                          "title": "Forbidden",
                          "status": 403,
                          "detail": "Only the host may modify invitees",
                          "code": "NOT_OWNER",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(
                responseCode = "404",
                description = "Meeting not found",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notFound", value = """
                        {
                          "type": "about:blank",
                          "title": "Not Found",
                          "status": 404,
                          "detail": "Meeting not found",
                          "code": "MEETING_NOT_FOUND",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }""")))
    })
    @PutMapping("/meetings/{id}/invitees")
    public ResponseEntity<Object> updateInvitees(
            @PathVariable UUID id, @Valid @RequestBody UpdateMeetingInviteesRequest request) {
        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return ResponseEntity.badRequest()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                    .body(org.springframework.http.ProblemDetail.forStatusAndDetail(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "X-Account-Id header is required"));
        }
        Result<UpdateMeetingInviteesResult, MeetingError> result =
                updateMeetingInviteesUseCase.execute(
                        request.toCommand(id, accountId, TenantContext.getCurrentTenant()));
        return responder.ok(result.map(UpdateMeetingInviteesResponse::from));
    }

    @Operation(
            summary = "Create an instant meeting",
            description = "Creates a new INSTANT meeting, auto-starts it to RUNNING,"
                    + " registers invitees with invite tokens, and"
                    + " returns a meeting snapshot plus a LiveKit HOST access token.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Instant meeting created",
                headers =
                        @Header(
                                name = "Location",
                                description = "URI of the newly created meeting",
                                schema = @Schema(type = "string", format = "uri")),
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @Schema(
                                                implementation =
                                                        CreateInstantMeetingResponse.class),
                                examples = @ExampleObject(name = "created", value = """
                        {
                          "meeting": {
                            "id": "0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90",
                            "hostId": "account-123",
                            "shortCode": "abc-defg-hij",
                            "type": "INSTANT",
                            "status": "RUNNING",
                            "title": "Daily standup",
                            "description": "Quick sync on progress",
                            "issueLink": {
                              "issueId": "10001",
                              "issueKey": "PROJ-1",
                              "projectKey": "PROJ"
                            },
                            "settings": {
                              "admissionPolicy": "OPEN",
                              "maxParticipants": 50,
                              "allowScreenShare": true,
                              "chatEnabled": true,
                              "allowMicrophone": true,
                              "allowVideo": true
                            },
                            "createdAt": "2025-01-15T10:30:00Z"
                          },
                          "livekit": {
                            "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.example.token",
                            "roomName": "meeting-0195e0c2"
                          }
                        }"""))),
        @ApiResponse(
                responseCode = "400",
                description = "Validation error or missing account header",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = {
                                    @ExampleObject(
                                            name = "validationError",
                                            summary = "Validation failure",
                                            value = """
                            {
                              "type": "about:blank",
                              "title": "Bad Request",
                              "status": 400,
                              "detail": "The request body failed validation",
                              "code": "VALIDATION_ERROR",
                              "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a",
                              "errors": [
                                {"field": "settings", "code": "REQUIRED", "message": "must not be null"}
                              ]
                            }"""),
                                    @ExampleObject(
                                            name = "missingAccount",
                                            summary = "Missing X-Account-Id header",
                                            value = """
                            {
                              "type": "about:blank",
                              "title": "Bad Request",
                              "status": 400,
                              "detail": "X-Account-Id header is required",
                              "code": "VALIDATION_ERROR",
                              "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                            }""")
                                }))
    })
    @PostMapping("/meetings:instant")
    public ResponseEntity<Object> createInstant(
            @Valid @RequestBody CreateInstantMeetingRequest request) {

        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return ResponseEntity.badRequest()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                    .body(org.springframework.http.ProblemDetail.forStatusAndDetail(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "X-Account-Id header is required"));
        }

        String tenantId = TenantContext.getCurrentTenant();
        CreateInstantMeetingCommand command = request.toCommand(accountId, tenantId);
        Result<CreateInstantMeetingResult, MeetingError> result =
                createInstantMeetingUseCase.execute(command);

        return responder.created(result.map(CreateInstantMeetingResponse::from), response -> {
            URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/1/meetings/{id}")
                    .buildAndExpand(response.meeting().id())
                    .toUri();
            return location;
        });
    }

    @Operation(
            summary = "Create a scheduled meeting",
            description = "Creates a new SCHEDULED meeting with a future time range."
                    + " The meeting stays in SCHEDULED status and no LiveKit token is issued.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Scheduled meeting created",
                headers =
                        @Header(
                                name = "Location",
                                description = "URI of the newly created meeting",
                                schema = @Schema(type = "string", format = "uri")),
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ScheduleMeetingResponse.class),
                                examples = @ExampleObject(name = "created", value = """
                        {
                          "meeting": {
                            "id": "0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90",
                            "hostId": "account-123",
                            "shortCode": "abc-defg-hij",
                            "type": "SCHEDULED",
                            "status": "SCHEDULED",
                            "title": "Sprint planning",
                            "description": "Plan the next sprint",
                            "issueLink": {
                              "issueId": "10001",
                              "issueKey": "PROJ-1",
                              "projectKey": "PROJ"
                            },
                            "settings": {
                              "admissionPolicy": "OPEN",
                              "maxParticipants": 50,
                              "allowScreenShare": true,
                              "chatEnabled": true,
                              "allowMicrophone": true,
                              "allowVideo": true
                            },
                            "startTime": "2025-02-01T14:00:00Z",
                            "endTime": "2025-02-01T15:00:00Z",
                            "createdAt": "2025-01-15T10:30:00Z"
                          }
                        }"""))),
        @ApiResponse(
                responseCode = "400",
                description = "Validation error, missing account header, or start time in past",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = {
                                    @ExampleObject(
                                            name = "validationError",
                                            summary = "Validation failure",
                                            value = """
                            {
                              "type": "about:blank",
                              "title": "Bad Request",
                              "status": 400,
                              "detail": "The request body failed validation",
                              "code": "VALIDATION_ERROR",
                              "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a",
                              "errors": [
                                {"field": "settings", "code": "REQUIRED", "message": "must not be null"}
                              ]
                            }"""),
                                    @ExampleObject(
                                            name = "startTimeInPast",
                                            summary = "Start time is in the past",
                                            value = """
                            {
                              "type": "about:blank",
                              "title": "Start time is in the past",
                              "status": 400,
                              "detail": "The scheduled start time is in the past: {0}.",
                              "code": "MEETING_START_IN_PAST",
                              "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                            }""")
                                }))
    })
    @PostMapping("/meetings:schedule")
    public ResponseEntity<Object> schedule(@Valid @RequestBody ScheduleMeetingRequest request) {

        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return ResponseEntity.badRequest()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                    .body(org.springframework.http.ProblemDetail.forStatusAndDetail(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "X-Account-Id header is required"));
        }

        String tenantId = TenantContext.getCurrentTenant();
        ScheduleMeetingCommand command = request.toCommand(accountId, tenantId);
        Result<ScheduleMeetingResult, MeetingError> result =
                scheduleMeetingUseCase.execute(command);

        return responder.created(result.map(ScheduleMeetingResponse::from), response -> {
            URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/1/meetings/{id}")
                    .buildAndExpand(response.meeting().id())
                    .toUri();
            return location;
        });
    }

    @Operation(
            summary = "Delete a meeting",
            description =
                    "Soft-deletes a meeting as its host. Running meetings must be ended first, "
                            + "and already-deleted meetings are treated as not found.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Meeting deleted; returns the deleted meeting snapshot",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = DeleteMeetingResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Missing account header",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "missingAccount", value = """
                        {
                          "type": "about:blank",
                          "title": "Bad Request",
                          "status": 400,
                          "detail": "X-Account-Id header is required",
                          "code": "VALIDATION_ERROR",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(
                responseCode = "403",
                description = "Only the host may delete the meeting",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notAuthorized", value = """
                        {
                          "type": "about:blank",
                          "title": "Not authorized",
                          "status": 403,
                          "detail": "You are not the host of this meeting.",
                          "code": "NOT_AUTHORIZED",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(
                responseCode = "404",
                description = "Meeting not found or already deleted",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notFound", value = """
                        {
                          "type": "about:blank",
                          "title": "Meeting not found",
                          "status": 404,
                          "detail": "No meeting matches the given identifier.",
                          "code": "MEETING_NOT_FOUND",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(
                responseCode = "409",
                description = "The meeting is running and cannot be deleted",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "running", value = """
                        {
                          "type": "about:blank",
                          "title": "Cannot delete a running meeting",
                          "status": 409,
                          "detail": "The meeting is running and must be ended before deletion.",
                          "code": "CANNOT_DELETE_RUNNING_MEETING",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }""")))
    })
    @DeleteMapping("/meetings/{id}")
    public ResponseEntity<Object> delete(@PathVariable UUID id) {
        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return ResponseEntity.badRequest()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                    .body(org.springframework.http.ProblemDetail.forStatusAndDetail(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "X-Account-Id header is required"));
        }
        Result<DeleteMeetingResult, MeetingError> result = deleteMeetingUseCase.execute(
                new io.github.smiskinext.meet.application.command.DeleteMeetingCommand(
                        id, TenantContext.getCurrentTenant(), accountId));
        return responder.ok(result.map(DeleteMeetingResponse::from));
    }

    @Operation(
            summary = "Batch delete meetings",
            description = "Atomically soft-deletes a non-empty list of meetings as their host. "
                    + "The batch is all-or-nothing: if any meeting is not found, already deleted, "
                    + "running, or hosted by another account, no meeting is deleted.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "All listed meetings deleted; returns the deleted meeting snapshots",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @Schema(
                                                implementation =
                                                        BatchDeleteMeetingsResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Validation error or missing account header",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = {
                                    @ExampleObject(
                                            name = "validationError",
                                            summary = "Empty identifier list",
                                            value = """
                            {
                              "type": "about:blank",
                              "title": "Bad Request",
                              "status": 400,
                              "detail": "The request body failed validation",
                              "code": "VALIDATION_ERROR",
                              "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a",
                              "errors": [
                                {"field": "meetingIds", "code": "REQUIRED", "message": "must not be empty"}
                              ]
                            }"""),
                                    @ExampleObject(
                                            name = "missingAccount",
                                            summary = "Missing X-Account-Id header",
                                            value = """
                            {
                              "type": "about:blank",
                              "title": "Bad Request",
                              "status": 400,
                              "detail": "X-Account-Id header is required",
                              "code": "VALIDATION_ERROR",
                              "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                            }""")
                                })),
        @ApiResponse(
                responseCode = "403",
                description = "Only the host may delete the meetings",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notAuthorized", value = """
                        {
                          "type": "about:blank",
                          "title": "Not authorized",
                          "status": 403,
                          "detail": "You are not the host of this meeting.",
                          "code": "NOT_AUTHORIZED",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(
                responseCode = "404",
                description = "A listed meeting was not found or already deleted",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notFound", value = """
                        {
                          "type": "about:blank",
                          "title": "Meeting not found",
                          "status": 404,
                          "detail": "No meeting matches the given identifier.",
                          "code": "MEETING_NOT_FOUND",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(
                responseCode = "409",
                description = "A listed meeting is running and cannot be deleted",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "running", value = """
                        {
                          "type": "about:blank",
                          "title": "Cannot delete a running meeting",
                          "status": 409,
                          "detail": "The meeting is running and must be ended before deletion.",
                          "code": "CANNOT_DELETE_RUNNING_MEETING",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }""")))
    })
    @PostMapping("/meetings:batchDelete")
    public ResponseEntity<Object> batchDelete(
            @Valid @RequestBody BatchDeleteMeetingsRequest request) {
        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return ResponseEntity.badRequest()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                    .body(org.springframework.http.ProblemDetail.forStatusAndDetail(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "X-Account-Id header is required"));
        }
        Result<BatchDeleteMeetingsResult, MeetingError> result = batchDeleteMeetingsUseCase.execute(
                request.toCommand(accountId, TenantContext.getCurrentTenant()));
        return responder.ok(result.map(BatchDeleteMeetingsResponse::from));
    }
}
