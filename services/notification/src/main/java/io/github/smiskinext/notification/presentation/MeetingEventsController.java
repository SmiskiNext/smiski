package io.github.smiskinext.notification.presentation;

import io.github.smiskinext.notification.application.command.SubscribeMeetingEventsCommand;
import io.github.smiskinext.notification.application.usecase.SubscribeMeetingEventsUseCase;
import io.swagger.v3.oas.annotations.Operation;
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
                    + "sends periodic heartbeat comments until the configured timeout.")
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
                    + "recorded before subscribe, and sends periodic heartbeat comments until the "
                    + "configured timeout.")
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
