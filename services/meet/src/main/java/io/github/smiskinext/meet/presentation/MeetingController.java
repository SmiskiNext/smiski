package io.github.smiskinext.meet.presentation;

import io.github.smiskinext.meet.application.command.AcceptMeetingInviteeCommand;
import io.github.smiskinext.meet.application.command.CancelMeetingCommand;
import io.github.smiskinext.meet.application.command.CreateInstantMeetingCommand;
import io.github.smiskinext.meet.application.command.DeclineMeetingInviteeCommand;
import io.github.smiskinext.meet.application.command.EndMeetingCommand;
import io.github.smiskinext.meet.application.command.ScheduleMeetingCommand;
import io.github.smiskinext.meet.application.command.TentativeMeetingInviteeCommand;
import io.github.smiskinext.meet.application.query.GetMeetingQuery;
import io.github.smiskinext.meet.application.result.AcceptJoinRequestsResult;
import io.github.smiskinext.meet.application.result.AcceptMeetingInviteeResult;
import io.github.smiskinext.meet.application.result.AddMeetingInviteesResult;
import io.github.smiskinext.meet.application.result.BatchDeleteMeetingsResult;
import io.github.smiskinext.meet.application.result.CancelMeetingResult;
import io.github.smiskinext.meet.application.result.CreateInstantMeetingResult;
import io.github.smiskinext.meet.application.result.DeclineJoinRequestsResult;
import io.github.smiskinext.meet.application.result.DeclineMeetingInviteeResult;
import io.github.smiskinext.meet.application.result.DeleteMeetingResult;
import io.github.smiskinext.meet.application.result.EndMeetingResult;
import io.github.smiskinext.meet.application.result.GetMeetingResult;
import io.github.smiskinext.meet.application.result.ListMeetingsResult;
import io.github.smiskinext.meet.application.result.ListPendingJoinRequestsResult;
import io.github.smiskinext.meet.application.result.RemoveMeetingInviteesResult;
import io.github.smiskinext.meet.application.result.RequestJoinResult;
import io.github.smiskinext.meet.application.result.ScheduleMeetingResult;
import io.github.smiskinext.meet.application.result.TentativeMeetingInviteeResult;
import io.github.smiskinext.meet.application.result.UpdateMeetingResult;
import io.github.smiskinext.meet.application.result.UpdateMeetingSettingsResult;
import io.github.smiskinext.meet.application.usecase.AcceptJoinRequestsUseCase;
import io.github.smiskinext.meet.application.usecase.AcceptMeetingInviteeUseCase;
import io.github.smiskinext.meet.application.usecase.AddMeetingInviteesUseCase;
import io.github.smiskinext.meet.application.usecase.BatchDeleteMeetingsUseCase;
import io.github.smiskinext.meet.application.usecase.CancelMeetingUseCase;
import io.github.smiskinext.meet.application.usecase.CreateInstantMeetingUseCase;
import io.github.smiskinext.meet.application.usecase.DeclineJoinRequestsUseCase;
import io.github.smiskinext.meet.application.usecase.DeclineMeetingInviteeUseCase;
import io.github.smiskinext.meet.application.usecase.DeleteMeetingUseCase;
import io.github.smiskinext.meet.application.usecase.EndMeetingUseCase;
import io.github.smiskinext.meet.application.usecase.GetMeetingUseCase;
import io.github.smiskinext.meet.application.usecase.ListMeetingsUseCase;
import io.github.smiskinext.meet.application.usecase.ListPendingJoinRequestsUseCase;
import io.github.smiskinext.meet.application.usecase.RemoveMeetingInviteesUseCase;
import io.github.smiskinext.meet.application.usecase.RequestJoinUseCase;
import io.github.smiskinext.meet.application.usecase.ScheduleMeetingUseCase;
import io.github.smiskinext.meet.application.usecase.TentativeMeetingInviteeUseCase;
import io.github.smiskinext.meet.application.usecase.UpdateMeetingSettingsUseCase;
import io.github.smiskinext.meet.application.usecase.UpdateMeetingUseCase;
import io.github.smiskinext.meet.domain.ListMeetingsError;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.presentation.request.AddMeetingInviteesRequest;
import io.github.smiskinext.meet.presentation.request.BatchDeleteMeetingsRequest;
import io.github.smiskinext.meet.presentation.request.CreateInstantMeetingRequest;
import io.github.smiskinext.meet.presentation.request.HandleJoinRequestsRequest;
import io.github.smiskinext.meet.presentation.request.JoinMeetingRequest;
import io.github.smiskinext.meet.presentation.request.ListMeetingsRequest;
import io.github.smiskinext.meet.presentation.request.RemoveMeetingInviteesRequest;
import io.github.smiskinext.meet.presentation.request.ScheduleMeetingRequest;
import io.github.smiskinext.meet.presentation.request.UpdateMeetingRequest;
import io.github.smiskinext.meet.presentation.request.UpdateMeetingSettingsRequest;
import io.github.smiskinext.meet.presentation.response.AddMeetingInviteesResponse;
import io.github.smiskinext.meet.presentation.response.BatchDeleteMeetingsResponse;
import io.github.smiskinext.meet.presentation.response.CancelMeetingResponse;
import io.github.smiskinext.meet.presentation.response.CreateInstantMeetingResponse;
import io.github.smiskinext.meet.presentation.response.DeleteMeetingResponse;
import io.github.smiskinext.meet.presentation.response.EndMeetingResponse;
import io.github.smiskinext.meet.presentation.response.GetMeetingResponse;
import io.github.smiskinext.meet.presentation.response.JoinDecisionResponse;
import io.github.smiskinext.meet.presentation.response.JoinMeetingResponse;
import io.github.smiskinext.meet.presentation.response.ListPendingJoinRequestsResponse;
import io.github.smiskinext.meet.presentation.response.MeetingInviteeResponse;
import io.github.smiskinext.meet.presentation.response.MeetingListPageResponse;
import io.github.smiskinext.meet.presentation.response.MeetingSummaryResponse;
import io.github.smiskinext.meet.presentation.response.RemoveMeetingInviteesResponse;
import io.github.smiskinext.meet.presentation.response.ScheduleMeetingResponse;
import io.github.smiskinext.meet.presentation.response.UpdateMeetingResponse;
import io.github.smiskinext.meet.presentation.response.UpdateMeetingSettingsResponse;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final UpdateMeetingSettingsUseCase updateMeetingSettingsUseCase;
    private final AddMeetingInviteesUseCase addMeetingInviteesUseCase;
    private final RemoveMeetingInviteesUseCase removeMeetingInviteesUseCase;
    private final ListMeetingsUseCase listMeetingsUseCase;
    private final GetMeetingUseCase getMeetingUseCase;
    private final DeleteMeetingUseCase deleteMeetingUseCase;
    private final BatchDeleteMeetingsUseCase batchDeleteMeetingsUseCase;
    private final CancelMeetingUseCase cancelMeetingUseCase;
    private final EndMeetingUseCase endMeetingUseCase;
    private final RequestJoinUseCase requestJoinUseCase;
    private final ListPendingJoinRequestsUseCase listPendingJoinRequestsUseCase;
    private final AcceptJoinRequestsUseCase acceptJoinRequestsUseCase;
    private final DeclineJoinRequestsUseCase declineJoinRequestsUseCase;
    private final AcceptMeetingInviteeUseCase acceptMeetingInviteeUseCase;
    private final DeclineMeetingInviteeUseCase declineMeetingInviteeUseCase;
    private final TentativeMeetingInviteeUseCase tentativeMeetingInviteeUseCase;
    private final ResultResponder responder;

    public MeetingController(
            CreateInstantMeetingUseCase createInstantMeetingUseCase,
            ScheduleMeetingUseCase scheduleMeetingUseCase,
            UpdateMeetingUseCase updateMeetingUseCase,
            UpdateMeetingSettingsUseCase updateMeetingSettingsUseCase,
            AddMeetingInviteesUseCase addMeetingInviteesUseCase,
            RemoveMeetingInviteesUseCase removeMeetingInviteesUseCase,
            ListMeetingsUseCase listMeetingsUseCase,
            GetMeetingUseCase getMeetingUseCase,
            DeleteMeetingUseCase deleteMeetingUseCase,
            BatchDeleteMeetingsUseCase batchDeleteMeetingsUseCase,
            CancelMeetingUseCase cancelMeetingUseCase,
            EndMeetingUseCase endMeetingUseCase,
            RequestJoinUseCase requestJoinUseCase,
            ListPendingJoinRequestsUseCase listPendingJoinRequestsUseCase,
            AcceptJoinRequestsUseCase acceptJoinRequestsUseCase,
            DeclineJoinRequestsUseCase declineJoinRequestsUseCase,
            AcceptMeetingInviteeUseCase acceptMeetingInviteeUseCase,
            DeclineMeetingInviteeUseCase declineMeetingInviteeUseCase,
            TentativeMeetingInviteeUseCase tentativeMeetingInviteeUseCase,
            ResultResponder responder) {
        this.createInstantMeetingUseCase = createInstantMeetingUseCase;
        this.scheduleMeetingUseCase = scheduleMeetingUseCase;
        this.updateMeetingUseCase = updateMeetingUseCase;
        this.updateMeetingSettingsUseCase = updateMeetingSettingsUseCase;
        this.addMeetingInviteesUseCase = addMeetingInviteesUseCase;
        this.removeMeetingInviteesUseCase = removeMeetingInviteesUseCase;
        this.listMeetingsUseCase = listMeetingsUseCase;
        this.getMeetingUseCase = getMeetingUseCase;
        this.deleteMeetingUseCase = deleteMeetingUseCase;
        this.batchDeleteMeetingsUseCase = batchDeleteMeetingsUseCase;
        this.cancelMeetingUseCase = cancelMeetingUseCase;
        this.endMeetingUseCase = endMeetingUseCase;
        this.requestJoinUseCase = requestJoinUseCase;
        this.listPendingJoinRequestsUseCase = listPendingJoinRequestsUseCase;
        this.acceptJoinRequestsUseCase = acceptJoinRequestsUseCase;
        this.declineJoinRequestsUseCase = declineJoinRequestsUseCase;
        this.acceptMeetingInviteeUseCase = acceptMeetingInviteeUseCase;
        this.declineMeetingInviteeUseCase = declineMeetingInviteeUseCase;
        this.tentativeMeetingInviteeUseCase = tentativeMeetingInviteeUseCase;
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
    @PreAuthorize("hasAuthority('view-meeting')")
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
    @PreAuthorize("hasAuthority('view-meeting')")
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
            summary = "Update meeting information",
            description = "Updates a meeting's information as its host: title, description, "
                    + "issue link, zone ID, and time range. Mutable while scheduled or running; "
                    + "zone ID and time range are mutable only while scheduled. To replace the "
                    + "settings block, use PUT /meetings/{id}/settings instead.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Meeting information updated",
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
    @PreAuthorize("hasAuthority('edit-meeting')")
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
            summary = "Replace meeting settings",
            description = "Replaces the entire meeting settings block as its host: "
                    + "admission policy, participant limit, and media/chat permissions. "
                    + "Permitted while the meeting is SCHEDULED or RUNNING. When the change "
                    + "affects media permissions, every connected non-host participant's LiveKit "
                    + "publish permission is updated in real time on a best-effort basis; the "
                    + "host is never affected.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Settings updated",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @Schema(
                                                implementation =
                                                        UpdateMeetingSettingsResponse.class),
                                examples = @ExampleObject(name = "updated", value = """
                        {
                          "meetingId": "0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90",
                          "admissionPolicy": "ALLOW_ALL",
                          "maxParticipants": 50,
                          "allowScreenShare": true,
                          "chatEnabled": true,
                          "allowMicrophone": true,
                          "allowVideo": true
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
                                {"field": "maxParticipants", "code": "TOO_SHORT", "message": "must be between 2 and 100"}
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
                description = "Only the host may replace the settings",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notAuthorized", value = """
                        {
                          "type": "about:blank",
                          "title": "Forbidden",
                          "status": 403,
                          "detail": "Only the host may change the meeting settings",
                          "code": "NOT_AUTHORIZED",
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
    @PutMapping("/meetings/{id}/settings")
    @PreAuthorize("hasAuthority('edit-meeting')")
    public ResponseEntity<Object> updateSettings(
            @PathVariable UUID id, @Valid @RequestBody UpdateMeetingSettingsRequest request) {
        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return ResponseEntity.badRequest()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                    .body(org.springframework.http.ProblemDetail.forStatusAndDetail(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "X-Account-Id header is required"));
        }
        Result<UpdateMeetingSettingsResult, MeetingError> result =
                updateMeetingSettingsUseCase.execute(
                        request.toCommand(id, accountId, TenantContext.getCurrentTenant()));
        return responder.ok(result.map(UpdateMeetingSettingsResponse::from));
    }

    @Operation(
            summary = "Add invitees to a meeting",
            description = "Adds one or more new invitees to a SCHEDULED meeting as its host. "
                    + "Each new account is created with status NEEDS_ACTION. The request is atomic: "
                    + "if any submitted accountId is already an active invitee the whole batch is "
                    + "rejected with 409 INVITEE_ALREADY_EXISTS.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Invitees created",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = AddMeetingInviteesResponse.class),
                                examples = @ExampleObject(name = "created", value = """
                        {
                          "invitees": [
                            {
                              "id": "0195e0c2-8f3a-7c21-b9d4-3a2b1c4d5e60",
                              "accountId": "account-456",
                              "email": "alice@example.com",
                              "displayName": "Alice Nguyen",
                              "role": "REQ_PARTICIPANT",
                              "status": "NEEDS_ACTION",
                              "invitedAt": "2025-01-15T10:35:00Z",
                              "respondedAt": null
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
                description = "Only the host may add invitees",
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
                        }"""))),
        @ApiResponse(
                responseCode = "409",
                description = "An account is already an active invitee",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "alreadyExists", value = """
                        {
                          "type": "about:blank",
                          "title": "Invitee already exists",
                          "status": 409,
                          "detail": "Account account-456 is already an active invitee of this meeting.",
                          "code": "INVITEE_ALREADY_EXISTS",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }""")))
    })
    @PostMapping("/meetings/{id}/invitees")
    @PreAuthorize("hasAuthority('edit-meeting')")
    public ResponseEntity<Object> addInvitees(
            @PathVariable UUID id, @Valid @RequestBody AddMeetingInviteesRequest request) {
        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return ResponseEntity.badRequest()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                    .body(org.springframework.http.ProblemDetail.forStatusAndDetail(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "X-Account-Id header is required"));
        }
        Result<AddMeetingInviteesResult, MeetingError> result = addMeetingInviteesUseCase.execute(
                request.toCommand(id, accountId, TenantContext.getCurrentTenant()));
        return responder.ok(result.map(AddMeetingInviteesResponse::from));
    }

    @Operation(
            summary = "Remove invitees from a meeting",
            description = "Removes one or more invitees from a SCHEDULED meeting as its host, by "
                    + "invitee id. The request is atomic: if any submitted id does not correspond to "
                    + "an active invitee of the meeting the whole batch is rejected with 404 "
                    + "INVITEE_NOT_FOUND.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Invitees removed",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @Schema(
                                                implementation =
                                                        RemoveMeetingInviteesResponse.class),
                                examples = @ExampleObject(name = "removed", value = """
                        {
                          "invitees": [
                            {
                              "id": "0195e0c2-8f3a-7c21-b9d4-3a2b1c4d5e60",
                              "accountId": "account-456",
                              "email": "alice@example.com",
                              "displayName": "Alice Nguyen",
                              "role": "REQ_PARTICIPANT",
                              "status": "NEEDS_ACTION",
                              "invitedAt": "2025-01-15T10:35:00Z",
                              "respondedAt": null
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
                                {"field": "inviteeIds", "code": "REQUIRED", "message": "must not be empty"}
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
                description = "Only the host may remove invitees",
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
                description = "Meeting or invitee not found",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = {
                                    @ExampleObject(name = "meetingNotFound", value = """
                        {
                          "type": "about:blank",
                          "title": "Not Found",
                          "status": 404,
                          "detail": "Meeting not found",
                          "code": "MEETING_NOT_FOUND",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""),
                                    @ExampleObject(name = "inviteeNotFound", value = """
                        {
                          "type": "about:blank",
                          "title": "Invitee not found",
                          "status": 404,
                          "detail": "No invitee matches: 0195e0c2-8f3a-7c21-b9d4-3a2b1c4d5e60.",
                          "code": "INVITEE_NOT_FOUND",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }""")
                                }))
    })
    @PostMapping("/meetings/{id}/invitees:batchDelete")
    @PreAuthorize("hasAuthority('edit-meeting')")
    public ResponseEntity<Object> batchDeleteInvitees(
            @PathVariable UUID id, @Valid @RequestBody RemoveMeetingInviteesRequest request) {
        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return ResponseEntity.badRequest()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                    .body(org.springframework.http.ProblemDetail.forStatusAndDetail(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "X-Account-Id header is required"));
        }
        Result<RemoveMeetingInviteesResult, MeetingError> result =
                removeMeetingInviteesUseCase.execute(
                        request.toCommand(id, accountId, TenantContext.getCurrentTenant()));
        return responder.ok(result.map(RemoveMeetingInviteesResponse::from));
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
    @PreAuthorize("hasAuthority('edit-meeting')")
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
    @PreAuthorize("hasAuthority('edit-meeting')")
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
                                schema = @Schema(implementation = DeleteMeetingResponse.class),
                                examples = @ExampleObject(name = "deleted", value = """
                        {
                          "meeting": {
                            "id": "0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90",
                            "hostId": "account-123",
                            "shortCode": "abc-defg-hij",
                            "type": "SCHEDULED",
                            "status": "DELETED",
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
                            "zoneId": "Asia/Ho_Chi_Minh",
                            "organizerEmail": "host@example.com",
                            "organizerDisplayName": "Host User",
                            "calendarUid": "meeting-0195e0c2@smiski.app",
                            "calendarSequence": 0,
                            "createdAt": "2025-01-15T10:30:00Z",
                            "deletedAt": "2025-01-20T09:00:00Z",
                            "deletedBy": "account-123"
                          }
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
    @PreAuthorize("hasAuthority('edit-meeting')")
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
                                        @Schema(implementation = BatchDeleteMeetingsResponse.class),
                                examples = @ExampleObject(name = "batchDeleted", value = """
                        {
                          "meetings": [
                            {
                              "id": "0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90",
                              "hostId": "account-123",
                              "shortCode": "abc-defg-hij",
                              "type": "SCHEDULED",
                              "status": "DELETED",
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
                              "zoneId": "Asia/Ho_Chi_Minh",
                              "organizerEmail": "host@example.com",
                              "organizerDisplayName": "Host User",
                              "calendarUid": "meeting-0195e0c2@smiski.app",
                              "calendarSequence": 0,
                              "createdAt": "2025-01-15T10:30:00Z",
                              "deletedAt": "2025-01-20T09:00:00Z",
                              "deletedBy": "account-123"
                            }
                          ]
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
    @PreAuthorize("hasAuthority('edit-meeting')")
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

    @Operation(
            summary = "Join a meeting",
            description = "Joins a meeting as an authenticated account. Under ALLOW_ALL admission "
                    + "the caller is admitted immediately with status APPROVED, a LiveKit token, "
                    + "and the room name. Under MANUAL_APPROVAL a pending request is created with "
                    + "status PENDING and a requestId; both outcomes return 200.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Join accepted (APPROVED) or pending host approval (PENDING)",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = JoinMeetingResponse.class),
                                examples = {
                                    @ExampleObject(name = "approved", value = """
                        {
                          "requestId": "0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90",
                          "status": "APPROVED",
                          "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.example.token",
                          "roomName": "meeting-0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90"
                        }"""),
                                    @ExampleObject(name = "pending", value = """
                        {
                          "requestId": "0195e0c2-8f3a-7c21-b9d4-3a2b1c4d5e60",
                          "status": "PENDING",
                          "token": null,
                          "roomName": null
                        }""")
                                })),
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
                                            summary = "Blank display name or device id",
                                            value = """
                            {
                              "type": "about:blank",
                              "title": "Bad Request",
                              "status": 400,
                              "detail": "The request body failed validation",
                              "code": "VALIDATION_ERROR",
                              "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a",
                              "errors": [
                                {"field": "displayName", "code": "REQUIRED", "message": "must not be blank"}
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
                responseCode = "404",
                description = "Meeting not found for the current tenant",
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
                description = "Meeting is at capacity",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "meetingFull", value = """
                        {
                          "type": "about:blank",
                          "title": "Meeting is full",
                          "status": 409,
                          "detail": "The meeting has reached its participant limit.",
                          "code": "MEETING_FULL",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }""")))
    })
    @PostMapping("/meetings/{id}:join")
    @PreAuthorize("hasAuthority('view-meeting')")
    public ResponseEntity<Object> join(
            @PathVariable UUID id, @Valid @RequestBody JoinMeetingRequest request) {
        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return ResponseEntity.badRequest()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                    .body(org.springframework.http.ProblemDetail.forStatusAndDetail(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "X-Account-Id header is required"));
        }
        Result<RequestJoinResult, MeetingError> result = requestJoinUseCase.execute(
                request.toCommand(id.toString(), accountId, TenantContext.getCurrentTenant()));
        return responder.ok(result.map(JoinMeetingResponse::from));
    }

    @Operation(
            summary = "List pending join requests",
            description = "Returns a paginated list of PENDING join requests for a meeting with "
                    + "MANUAL_APPROVAL admission policy. Only the meeting host may call this "
                    + "endpoint. Use offset and pageSize query parameters for pagination.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Paginated list of pending join requests",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @Schema(
                                                implementation =
                                                        ListPendingJoinRequestsResponse.class),
                                examples = @ExampleObject(name = "list", value = """
                        {
                          "results": [
                            {
                              "requestId": "0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90",
                              "accountId": "account-456",
                              "displayName": "Alice Nguyen",
                              "status": "PENDING",
                              "requestedAt": "2025-02-01T14:00:00Z",
                              "expiresAt": "2025-02-01T14:05:00Z"
                            }
                          ],
                          "meta": {
                            "total": 1,
                            "offset": 0,
                            "pageSize": 20
                          }
                        }"""))),
        @ApiResponse(
                responseCode = "400",
                description = "Missing account header or pageSize out of range",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = {
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
                            }"""),
                                    @ExampleObject(
                                            name = "pageSizeOutOfRange",
                                            summary = "pageSize is 0 or greater than 100",
                                            value = """
                            {
                              "type": "about:blank",
                              "title": "Bad Request",
                              "status": 400,
                              "detail": "pageSize must be between 1 and 100",
                              "code": "VALIDATION_ERROR",
                              "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                            }""")
                                })),
        @ApiResponse(
                responseCode = "403",
                description = "Only the host may list pending join requests",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notOwner", value = """
                        {
                          "type": "about:blank",
                          "title": "Not authorized",
                          "status": 403,
                          "detail": "You do not own the requested user scope.",
                          "code": "NOT_OWNER",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(
                responseCode = "404",
                description = "Meeting not found for the current tenant",
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
    @GetMapping("/meetings/{id}/join-requests")
    @PreAuthorize("hasAuthority('edit-meeting')")
    public ResponseEntity<Object> listPendingJoinRequests(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int pageSize) {
        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return missingAccount();
        }
        if (pageSize < 1 || pageSize > 100) {
            org.springframework.http.ProblemDetail problem =
                    org.springframework.http.ProblemDetail.forStatusAndDetail(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "pageSize must be between 1 and 100");
            problem.setProperty("code", "VALIDATION_ERROR");
            return ResponseEntity.badRequest()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }
        Result<ListPendingJoinRequestsResult, MeetingError> result =
                listPendingJoinRequestsUseCase.execute(
                        new io.github.smiskinext.meet.application.query
                                .ListPendingJoinRequestsQuery(
                                id, TenantContext.getCurrentTenant(), accountId, offset, pageSize));
        return responder.ok(result.map(ListPendingJoinRequestsResponse::from));
    }

    @Operation(
            summary = "Accept pending join requests",
            description =
                    "Accepts one or more pending join requests as the meeting host. Processing "
                            + "is best-effort per item: each submitted requestId yields a result entry with "
                            + "status APPROVED (carrying a LiveKit token and room name), or FAILED (carrying "
                            + "a machine-readable reason such as MEETING_FULL, JOIN_REQUEST_NOT_FOUND, or "
                            + "JOIN_REQUEST_EXPIRED). Capacity is enforced so the batch never exceeds "
                            + "maxParticipants. Only the host may accept.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Per-item decision results",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = JoinDecisionResponse.class),
                                examples = @ExampleObject(name = "results", value = """
                        {
                          "results": [
                            {
                              "requestId": "0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90",
                              "status": "APPROVED",
                              "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.example.token",
                              "roomName": "meeting-0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90",
                              "reason": null
                            },
                            {
                              "requestId": "0195e0c2-8f3a-7c21-b9d4-3a2b1c4d5e60",
                              "status": "FAILED",
                              "token": null,
                              "roomName": null,
                              "reason": "MEETING_FULL"
                            }
                          ]
                        }"""))),
        @ApiResponse(
                responseCode = "400",
                description = "Missing account header or empty/malformed body",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = {
                                    @ExampleObject(
                                            name = "validationError",
                                            summary = "Empty requestIds list",
                                            value = """
                            {
                              "type": "about:blank",
                              "title": "Bad Request",
                              "status": 400,
                              "detail": "The request body failed validation",
                              "code": "VALIDATION_ERROR",
                              "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a",
                              "errors": [
                                {"field": "requestIds", "code": "REQUIRED", "message": "must not be empty"}
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
                description = "Only the host may accept join requests",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notOwner", value = """
                        {
                          "type": "about:blank",
                          "title": "Not authorized",
                          "status": 403,
                          "detail": "You do not own the requested user scope.",
                          "code": "NOT_OWNER",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(
                responseCode = "404",
                description = "Meeting not found for the current tenant",
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
    @PostMapping("/meetings/{id}/join-requests:accept")
    @PreAuthorize("hasAuthority('edit-meeting')")
    public ResponseEntity<Object> acceptJoinRequests(
            @PathVariable UUID id, @Valid @RequestBody HandleJoinRequestsRequest request) {
        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return ResponseEntity.badRequest()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                    .body(org.springframework.http.ProblemDetail.forStatusAndDetail(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "X-Account-Id header is required"));
        }
        Result<AcceptJoinRequestsResult, MeetingError> result = acceptJoinRequestsUseCase.execute(
                request.toAcceptCommand(id, accountId, TenantContext.getCurrentTenant()));
        return responder.ok(result.map(JoinDecisionResponse::from));
    }

    @Operation(
            summary = "Decline pending join requests",
            description =
                    "Declines one or more pending join requests as the meeting host. Processing "
                            + "is best-effort per item: each submitted requestId yields a result entry with "
                            + "status DENIED (no token), or FAILED (carrying a machine-readable reason). Only "
                            + "the host may decline.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Per-item decision results",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = JoinDecisionResponse.class),
                                examples = @ExampleObject(name = "results", value = """
                        {
                          "results": [
                            {
                              "requestId": "0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90",
                              "status": "DENIED",
                              "token": null,
                              "roomName": null,
                              "reason": null
                            },
                            {
                              "requestId": "0195e0c2-8f3a-7c21-b9d4-3a2b1c4d5e60",
                              "status": "FAILED",
                              "token": null,
                              "roomName": null,
                              "reason": "JOIN_REQUEST_NOT_FOUND"
                            }
                          ]
                        }"""))),
        @ApiResponse(
                responseCode = "400",
                description = "Missing account header or empty/malformed body",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = {
                                    @ExampleObject(
                                            name = "validationError",
                                            summary = "Empty requestIds list",
                                            value = """
                            {
                              "type": "about:blank",
                              "title": "Bad Request",
                              "status": 400,
                              "detail": "The request body failed validation",
                              "code": "VALIDATION_ERROR",
                              "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a",
                              "errors": [
                                {"field": "requestIds", "code": "REQUIRED", "message": "must not be empty"}
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
                description = "Only the host may decline join requests",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notOwner", value = """
                        {
                          "type": "about:blank",
                          "title": "Not authorized",
                          "status": 403,
                          "detail": "You do not own the requested user scope.",
                          "code": "NOT_OWNER",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(
                responseCode = "404",
                description = "Meeting not found for the current tenant",
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
    @PostMapping("/meetings/{id}/join-requests:decline")
    @PreAuthorize("hasAuthority('edit-meeting')")
    public ResponseEntity<Object> declineJoinRequests(
            @PathVariable UUID id, @Valid @RequestBody HandleJoinRequestsRequest request) {
        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return ResponseEntity.badRequest()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                    .body(org.springframework.http.ProblemDetail.forStatusAndDetail(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "X-Account-Id header is required"));
        }
        Result<DeclineJoinRequestsResult, MeetingError> result = declineJoinRequestsUseCase.execute(
                request.toDeclineCommand(id, accountId, TenantContext.getCurrentTenant()));
        return responder.ok(result.map(JoinDecisionResponse::from));
    }

    @Operation(
            summary = "Accept a meeting invitation",
            description =
                    "Accepts the caller's own meeting invitation. The acting account, resolved "
                            + "from the account header, must own the target invitee. Returns the updated "
                            + "invitee snapshot on success.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Invitation accepted",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MeetingInviteeResponse.class),
                                examples = @ExampleObject(name = "accepted", value = """
                        {
                          "id": "0195e0c2-8f3a-7c21-b9d4-3a2b1c4d5e60",
                          "accountId": "account-456",
                          "email": "alice@example.com",
                          "displayName": "Alice Nguyen",
                          "role": "REQ_PARTICIPANT",
                          "status": "ACCEPTED",
                          "invitedAt": "2025-01-15T10:35:00Z",
                          "respondedAt": "2025-01-16T08:00:00Z"
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
                responseCode = "403",
                description = "The acting account does not own the target invitation",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notOwner", value = """
                        {
                          "type": "about:blank",
                          "title": "Not authorized",
                          "status": 403,
                          "detail": "You do not own the requested user scope.",
                          "code": "NOT_OWNER",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(
                responseCode = "404",
                description = "Meeting or invitee not found for the current tenant",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notFound", value = """
                        {
                          "type": "about:blank",
                          "title": "Invitee not found",
                          "status": 404,
                          "detail": "No invitee matches the given identifier.",
                          "code": "INVITEE_NOT_FOUND",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(
                responseCode = "409",
                description = "The current status does not permit accepting",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "invalidTransition", value = """
                        {
                          "type": "about:blank",
                          "title": "Invalid invitee transition",
                          "status": 409,
                          "detail": "Cannot transition from ACCEPTED to ACCEPTED.",
                          "code": "INVALID_INVITEE_TRANSITION",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }""")))
    })
    @PostMapping("/meetings/{id}/invitees/{inviteeId}:accept")
    public ResponseEntity<Object> acceptInvitation(
            @PathVariable UUID id, @PathVariable UUID inviteeId) {
        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return missingAccount();
        }
        Result<AcceptMeetingInviteeResult, MeetingError> result =
                acceptMeetingInviteeUseCase.execute(new AcceptMeetingInviteeCommand(
                        id, inviteeId, accountId, TenantContext.getCurrentTenant()));
        return responder.ok(result.map(MeetingInviteeResponse::from));
    }

    @Operation(
            summary = "Decline a meeting invitation",
            description =
                    "Declines the caller's own meeting invitation. The acting account, resolved "
                            + "from the account header, must own the target invitee. Returns the updated "
                            + "invitee snapshot on success.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Invitation declined",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MeetingInviteeResponse.class),
                                examples = @ExampleObject(name = "declined", value = """
                        {
                          "id": "0195e0c2-8f3a-7c21-b9d4-3a2b1c4d5e60",
                          "accountId": "account-456",
                          "email": "alice@example.com",
                          "displayName": "Alice Nguyen",
                          "role": "REQ_PARTICIPANT",
                          "status": "DECLINED",
                          "invitedAt": "2025-01-15T10:35:00Z",
                          "respondedAt": "2025-01-16T08:00:00Z"
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
                responseCode = "403",
                description = "The acting account does not own the target invitation",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notOwner", value = """
                        {
                          "type": "about:blank",
                          "title": "Not authorized",
                          "status": 403,
                          "detail": "You do not own the requested user scope.",
                          "code": "NOT_OWNER",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(
                responseCode = "404",
                description = "Meeting or invitee not found for the current tenant",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notFound", value = """
                        {
                          "type": "about:blank",
                          "title": "Invitee not found",
                          "status": 404,
                          "detail": "No invitee matches the given identifier.",
                          "code": "INVITEE_NOT_FOUND",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(
                responseCode = "409",
                description = "The current status does not permit declining",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "invalidTransition", value = """
                        {
                          "type": "about:blank",
                          "title": "Invalid invitee transition",
                          "status": 409,
                          "detail": "Cannot transition from DECLINED to DECLINED.",
                          "code": "INVALID_INVITEE_TRANSITION",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }""")))
    })
    @PostMapping("/meetings/{id}/invitees/{inviteeId}:decline")
    public ResponseEntity<Object> declineInvitation(
            @PathVariable UUID id, @PathVariable UUID inviteeId) {
        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return missingAccount();
        }
        Result<DeclineMeetingInviteeResult, MeetingError> result =
                declineMeetingInviteeUseCase.execute(new DeclineMeetingInviteeCommand(
                        id, inviteeId, accountId, TenantContext.getCurrentTenant()));
        return responder.ok(result.map(MeetingInviteeResponse::from));
    }

    @Operation(
            summary = "Tentatively respond to a meeting invitation",
            description =
                    "Marks the caller's own meeting invitation as tentative. The acting account, "
                            + "resolved from the account header, must own the target invitee. Returns the "
                            + "updated invitee snapshot on success.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Invitation marked tentative",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MeetingInviteeResponse.class),
                                examples = @ExampleObject(name = "tentative", value = """
                        {
                          "id": "0195e0c2-8f3a-7c21-b9d4-3a2b1c4d5e60",
                          "accountId": "account-456",
                          "email": "alice@example.com",
                          "displayName": "Alice Nguyen",
                          "role": "REQ_PARTICIPANT",
                          "status": "TENTATIVE",
                          "invitedAt": "2025-01-15T10:35:00Z",
                          "respondedAt": "2025-01-16T08:00:00Z"
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
                responseCode = "403",
                description = "The acting account does not own the target invitation",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notOwner", value = """
                        {
                          "type": "about:blank",
                          "title": "Not authorized",
                          "status": 403,
                          "detail": "You do not own the requested user scope.",
                          "code": "NOT_OWNER",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(
                responseCode = "404",
                description = "Meeting or invitee not found for the current tenant",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notFound", value = """
                        {
                          "type": "about:blank",
                          "title": "Invitee not found",
                          "status": 404,
                          "detail": "No invitee matches the given identifier.",
                          "code": "INVITEE_NOT_FOUND",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(
                responseCode = "409",
                description = "The current status does not permit a tentative response",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "invalidTransition", value = """
                        {
                          "type": "about:blank",
                          "title": "Invalid invitee transition",
                          "status": 409,
                          "detail": "Cannot transition from TENTATIVE to TENTATIVE.",
                          "code": "INVALID_INVITEE_TRANSITION",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }""")))
    })
    @PostMapping("/meetings/{id}/invitees/{inviteeId}:tentative")
    public ResponseEntity<Object> tentativeInvitation(
            @PathVariable UUID id, @PathVariable UUID inviteeId) {
        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return missingAccount();
        }
        Result<TentativeMeetingInviteeResult, MeetingError> result =
                tentativeMeetingInviteeUseCase.execute(new TentativeMeetingInviteeCommand(
                        id, inviteeId, accountId, TenantContext.getCurrentTenant()));
        return responder.ok(result.map(MeetingInviteeResponse::from));
    }

    @Operation(
            summary = "Cancel a meeting",
            description =
                    "Cancels a SCHEDULED meeting as its host. The reason is always HOST_CANCELED. "
                            + "A MeetingCanceledEvent is published through the transactional outbox, carrying "
                            + "the active invitee list. Only SCHEDULED meetings can be canceled via this endpoint.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Meeting canceled; returns the canceled meeting snapshot",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CancelMeetingResponse.class),
                                examples = @ExampleObject(name = "canceled", value = """
                        {
                          "meeting": {
                            "id": "0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90",
                            "hostId": "account-123",
                            "shortCode": "abc-defg-hij",
                            "type": "SCHEDULED",
                            "status": "CANCELED",
                            "cancelReason": "HOST_CANCELED",
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
                            "calendarSequence": 0,
                            "createdAt": "2025-01-15T10:30:00Z"
                          }
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
                responseCode = "403",
                description = "Only the host may cancel the meeting",
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
                description = "Meeting not found or soft-deleted",
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
                description = "Meeting status does not allow cancellation",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples =
                                        @ExampleObject(name = "invalidTransition", value = """
                        {
                          "type": "about:blank",
                          "title": "Invalid status transition",
                          "status": 409,
                          "detail": "Cannot transition from RUNNING to CANCELED.",
                          "code": "INVALID_STATUS_TRANSITION",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/meetings/{id}:cancel")
    @PreAuthorize("hasAuthority('edit-meeting')")
    public ResponseEntity<Object> cancel(@PathVariable UUID id) {
        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return missingAccount();
        }
        String tenantId = TenantContext.getCurrentTenant();
        Result<CancelMeetingResult, MeetingError> result =
                cancelMeetingUseCase.execute(new CancelMeetingCommand(id, tenantId, accountId));
        return responder.ok(result.map(CancelMeetingResponse::from));
    }

    @Operation(
            summary = "End a running meeting",
            description =
                    "Ends a RUNNING meeting as its host. Transitions the meeting to COMPLETED, "
                            + "closes all active participation logs, publishes a MeetingCompletedEvent through "
                            + "the transactional outbox, and requests best-effort deletion of the LiveKit room.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Meeting ended; returns the completed meeting snapshot",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = EndMeetingResponse.class),
                                examples = @ExampleObject(name = "completed", value = """
                        {
                          "meeting": {
                            "id": "0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90",
                            "hostId": "account-123",
                            "shortCode": "abc-defg-hij",
                            "type": "SCHEDULED",
                            "status": "COMPLETED",
                            "cancelReason": null,
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
                            "calendarSequence": 0,
                            "createdAt": "2025-01-15T10:30:00Z"
                          }
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
                responseCode = "403",
                description = "Only the host may end the meeting",
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
                description = "Meeting not found or soft-deleted",
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
                description = "Meeting status does not allow completion",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples =
                                        @ExampleObject(name = "invalidTransition", value = """
                        {
                          "type": "about:blank",
                          "title": "Invalid status transition",
                          "status": 409,
                          "detail": "Cannot transition from SCHEDULED to COMPLETED.",
                          "code": "INVALID_STATUS_TRANSITION",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/meetings/{id}:end")
    @PreAuthorize("hasAuthority('edit-meeting')")
    public ResponseEntity<Object> end(@PathVariable UUID id) {
        String accountId = AccountContext.getCurrentAccount().orElse(null);
        if (accountId == null) {
            return missingAccount();
        }
        String tenantId = TenantContext.getCurrentTenant();
        Result<EndMeetingResult, MeetingError> result =
                endMeetingUseCase.execute(new EndMeetingCommand(id, tenantId, accountId));
        return responder.ok(result.map(EndMeetingResponse::from));
    }

    private static ResponseEntity<Object> missingAccount() {
        org.springframework.http.ProblemDetail problem =
                org.springframework.http.ProblemDetail.forStatusAndDetail(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "X-Account-Id header is required");
        problem.setProperty("code", "VALIDATION_ERROR");
        return ResponseEntity.badRequest()
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
