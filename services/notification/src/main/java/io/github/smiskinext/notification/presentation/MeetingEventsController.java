package io.github.smiskinext.notification.presentation;

import io.github.smiskinext.notification.application.command.SubscribeMeetingEventsCommand;
import io.github.smiskinext.notification.application.usecase.SubscribeMeetingEventsUseCase;
import io.github.smiskinext.notification.presentation.response.JoinRequestApprovedData;
import io.github.smiskinext.notification.presentation.response.JoinRequestCreatedData;
import io.github.smiskinext.notification.presentation.response.JoinRequestDeniedData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events endpoint for meeting hosts to receive join-related notifications in real time.
 *
 * <p>Streams are scoped by meeting id only; host identity verification is the upstream gateway's
 * responsibility per the trust-boundary model. On subscribe, the meeting's currently pending join
 * requests are replayed so a host connecting after requests arrived still sees them.
 */
@RestController
@Tag(
        name = "meeting-events-controller",
        description = "Real-time meeting join notifications over Server-Sent Events")
public class MeetingEventsController {

    private final SubscribeMeetingEventsUseCase subscribeMeetingEventsUseCase;

    public MeetingEventsController(SubscribeMeetingEventsUseCase subscribeMeetingEventsUseCase) {
        this.subscribeMeetingEventsUseCase = subscribeMeetingEventsUseCase;
    }

    @Operation(
            summary = "Subscribe to a meeting's join events",
            description = "Opens a text/event-stream connection that delivers join_request_created "
                    + "events for the meeting, replays currently pending requests on subscribe, and "
                    + "sends periodic heartbeat comments (`: ka`) until the configured timeout. "
                    + "Each event is sent as an SSE frame with an `event: join_request_created` "
                    + "line followed by a `data:` line containing the JSON payload described in "
                    + "the schema.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "SSE stream opened; events delivered over text/event-stream",
                content =
                        @Content(
                                mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                                schema = @Schema(implementation = JoinRequestCreatedData.class),
                                examples =
                                        @ExampleObject(
                                                name = "joinRequestCreated",
                                                summary = "A new join request arrived",
                                                value = """
                            {"requestId":"018f4e2a-1b3c-7d8e-9f0a-1b2c3d4e5f6a","accountId":"018f4e2a-0000-7d8e-9f0a-1b2c3d4e5f6a","displayName":"Alice","avatarUrl":null}"""))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping(value = "/meetings/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable UUID id) {
        return subscribeMeetingEventsUseCase
                .execute(new SubscribeMeetingEventsCommand(id, false, null))
                .fold(result -> result.emitter(), error -> {
                    throw new IllegalStateException(
                            "Unexpected failure subscribing to meeting events: " + error);
                });
    }

    @Operation(
            summary = "Subscribe to a join request's decision",
            description = "Opens a text/event-stream connection scoped by request id that delivers "
                    + "the host's accept/decline outcome as a join_request_approved (token, "
                    + "roomName) or join_request_denied (reason) event, replays a decision already "
                    + "recorded before subscribe, and sends periodic heartbeat comments (`: ka`) "
                    + "until the configured timeout. Each event is sent as an SSE frame with an "
                    + "`event:` line (either `join_request_approved` or `join_request_denied`) "
                    + "followed by a `data:` line containing the JSON payload described in the "
                    + "schema.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "SSE stream opened; decision delivered over text/event-stream",
                content =
                        @Content(
                                mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                                schema =
                                        @Schema(
                                                anyOf = {
                                                    JoinRequestApprovedData.class,
                                                    JoinRequestDeniedData.class
                                                }),
                                examples = {
                                    @ExampleObject(
                                            name = "joinRequestApproved",
                                            summary = "Host approved; token + room delivered",
                                            value = """
                            {"token":"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.example.token","roomName":"room-018f4e2a"}"""),
                                    @ExampleObject(
                                            name = "joinRequestDenied",
                                            summary =
                                                    "Host declined; optional reason, never a token",
                                            value = """
                            {"reason":"HOST_DECLINED"}""")
                                })),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping(
            value = "/meetings/{id}/join-requests/{requestId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeRequest(@PathVariable UUID id, @PathVariable UUID requestId) {
        return subscribeMeetingEventsUseCase
                .execute(new SubscribeMeetingEventsCommand(id, true, requestId))
                .fold(result -> result.emitter(), error -> {
                    throw new IllegalStateException(
                            "Unexpected failure subscribing to join request events: " + error);
                });
    }
}
