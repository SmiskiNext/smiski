package io.github.smiskinext.meet.presentation;

import io.github.smiskinext.meet.application.command.CreateInstantMeetingCommand;
import io.github.smiskinext.meet.application.result.CreateInstantMeetingResult;
import io.github.smiskinext.meet.application.usecase.CreateInstantMeetingUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.presentation.request.CreateInstantMeetingRequest;
import io.github.smiskinext.meet.presentation.response.CreateInstantMeetingResponse;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.infrastructure.identity.AccountContext;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import io.github.smiskinext.shared.infrastructure.web.ProblemDetailSchema;
import io.github.smiskinext.shared.infrastructure.web.ResultResponder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class MeetingController {

    private final CreateInstantMeetingUseCase createInstantMeetingUseCase;
    private final ResultResponder responder;

    public MeetingController(
            CreateInstantMeetingUseCase createInstantMeetingUseCase, ResultResponder responder) {
        this.createInstantMeetingUseCase = createInstantMeetingUseCase;
        this.responder = responder;
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
                                                        CreateInstantMeetingResponse.class))),
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
}
