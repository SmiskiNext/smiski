package io.github.smiskinext.meetingmanagement.presentation;

import io.github.smiskinext.meetingmanagement.application.command.ActivateRecordingCommand;
import io.github.smiskinext.meetingmanagement.application.command.AssignSidCommand;
import io.github.smiskinext.meetingmanagement.application.command.CloseStaleMeetingLogsCommand;
import io.github.smiskinext.meetingmanagement.application.command.FinalizeRecordingCommand;
import io.github.smiskinext.meetingmanagement.application.command.LeaveMeetingCommand;
import io.github.smiskinext.meetingmanagement.application.usecase.ActivateRecordingUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.AssignSidUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.CloseStaleMeetingLogsUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.FinalizeRecordingUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.LeaveMeetingUseCase;
import io.github.smiskinext.meetingmanagement.domain.event.ParticipantJoinedEvent;
import io.github.smiskinext.meetingmanagement.domain.event.ParticipantLeftEvent;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meetingmanagement.domain.port.EventPublisher;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import io.github.smiskinext.meetingmanagement.infrastructure.config.LiveKitProperties;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitParticipantSid;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meetingmanagement.infrastructure.security.SecurityConfig;
import io.livekit.server.WebhookReceiver;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.Instant;
import java.util.UUID;
import livekit.LivekitWebhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives LiveKit server-side webhook events and delegates to the appropriate use cases.
 *
 * <p>LiveKit POSTs events to this endpoint with {@code Content-Type: application/webhook+json}.
 * The request body is the raw JSON payload; the {@code Authorization} header contains a signed
 * JWT whose {@code sha256} claim is a hash of the raw body. The {@link WebhookReceiver} from the
 * LiveKit Java SDK verifies both the JWT signature and the body hash before processing.
 *
 * <p>This endpoint is {@code permitAll} in {@link
 * SecurityConfig} because
 * LiveKit requests do not carry an {@code X-User-ID} header. Security is provided entirely by
 * the webhook signature verification.
 *
 * <p>The endpoint <b>always returns HTTP 200</b> after verification, even if a use case reports
 * a non-fatal condition (e.g. log not found). Any non-2xx response would cause LiveKit to retry
 * the event, which is undesirable for idempotent warnings.
 *
 * <p>Handled events:
 * <ul>
 *   <li>{@code participant_joined} — assigns the LiveKit participant SID to the pending
 *       participation log created at token-issuance time.</li>
 *   <li>{@code participant_left} — records the participant's departure.</li>
 *   <li>{@code room_finished} — bulk-closes any remaining active participation logs for the
 *       meeting (safety net for missed {@code participant_left} events).</li>
 *   <li>{@code egress_started} — marks a known recording as actively recording.</li>
 *   <li>{@code egress_ended} — completes or fails a known recording using LiveKit output
 *       metadata.</li>
 * </ul>
 */
@Hidden
@RestController
public class LiveKitWebhookController {

    private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookController.class);

    private static final String ROOM_NAME_PREFIX = "meeting-";

    private final WebhookReceiver webhookReceiver;
    private final AssignSidUseCase assignSidUseCase;
    private final LeaveMeetingUseCase leaveMeetingUseCase;
    private final CloseStaleMeetingLogsUseCase closeStaleMeetingLogsUseCase;
    private final ActivateRecordingUseCase activateRecordingUseCase;
    private final FinalizeRecordingUseCase finalizeRecordingUseCase;
    private final EventPublisher eventPublisher;
    private final ParticipationLogRepository participationLogRepository;
    private final LiveKitProperties liveKitProperties;

    public LiveKitWebhookController(
            LiveKitProperties liveKitProperties,
            AssignSidUseCase assignSidUseCase,
            LeaveMeetingUseCase leaveMeetingUseCase,
            CloseStaleMeetingLogsUseCase closeStaleMeetingLogsUseCase,
            ActivateRecordingUseCase activateRecordingUseCase,
            FinalizeRecordingUseCase finalizeRecordingUseCase,
            EventPublisher eventPublisher,
            ParticipationLogRepository participationLogRepository) {
        this.webhookReceiver = new WebhookReceiver(
                liveKitProperties.getApiKey(), liveKitProperties.getApiSecret());
        this.assignSidUseCase = assignSidUseCase;
        this.leaveMeetingUseCase = leaveMeetingUseCase;
        this.closeStaleMeetingLogsUseCase = closeStaleMeetingLogsUseCase;
        this.activateRecordingUseCase = activateRecordingUseCase;
        this.finalizeRecordingUseCase = finalizeRecordingUseCase;
        this.eventPublisher = eventPublisher;
        this.participationLogRepository = participationLogRepository;
        this.liveKitProperties = liveKitProperties;
    }

    /**
     * Receives a LiveKit webhook event.
     *
     * <p>The {@code @RequestBody String rawBody} binding is intentional — Spring must NOT parse
     * the JSON before this method receives it. The LiveKit SDK verifies the SHA-256 hash of the
     * exact raw bytes; any framework-level JSON parsing (whitespace normalization, field reordering)
     * would break signature verification.
     */
    @PostMapping(
            value = "/{version}/webhook/livekit",
            version = "1.0",
            consumes = {"application/webhook+json", "application/json"})
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        LivekitWebhook.WebhookEvent event;
        try {
            event = webhookReceiver.receive(rawBody, authHeader);
        } catch (Exception e) {
            log.warn("LiveKit webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        }

        String eventType = event.getEvent();
        log.debug("Received LiveKit webhook event: {}", eventType);

        try {
            switch (eventType) {
                case "participant_joined" -> handleParticipantJoined(event);
                case "participant_left" -> handleParticipantLeft(event);
                case "room_finished" -> handleRoomFinished(event);
                case "egress_started" -> handleEgressStarted(event);
                case "egress_ended" -> handleEgressEnded(event);
                default -> log.trace("Ignoring LiveKit webhook event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error(
                    "Unexpected error processing LiveKit webhook event '{}': {}",
                    eventType,
                    e.getMessage(),
                    e);
        }

        return ResponseEntity.ok().build();
    }

    private void handleParticipantJoined(LivekitWebhook.WebhookEvent event) {
        var meetingId = extractMeetingId(event.getRoom().getName());
        if (meetingId == null) return;

        var participant = event.getParticipant();
        assignSidUseCase.execute(
                new AssignSidCommand(meetingId, participant.getIdentity(), participant.getSid()));

        // Look up the participation log to get userId and displayName.
        var identity = LiveKitIdentity.of(participant.getIdentity());
        var logOpt =
                participationLogRepository.findActiveByMeetingIdAndIdentity(meetingId, identity);
        if (logOpt.isPresent()) {
            var log = logOpt.get();
            var joinedEvent = new ParticipantJoinedEvent(
                    UUID.randomUUID(),
                    meetingId,
                    log.getUserId().orElse(null),
                    log.getDisplayName(),
                    Instant.now());
            eventPublisher.publish(joinedEvent);
        }
    }

    private void handleParticipantLeft(LivekitWebhook.WebhookEvent event) {
        var meetingId = extractMeetingId(event.getRoom().getName());
        if (meetingId == null) return;

        var sid = event.getParticipant().getSid();
        // Look up the participation log BEFORE calling the use case (use case returns Result
        // without exposing the domain object) so we have displayName for the event.
        var found =
                participationLogRepository.findActiveBySid(LiveKitParticipantSid.of(sid));
        if (found.isPresent()) {
            var log = found.get();
            var leftEvent = new ParticipantLeftEvent(
                    UUID.randomUUID(),
                    meetingId,
                    log.getUserId().orElse(null),
                    log.getDisplayName(),
                    Instant.now());
            eventPublisher.publish(leftEvent);
        }

        leaveMeetingUseCase.execute(new LeaveMeetingCommand(meetingId, sid));
    }

    private void handleRoomFinished(LivekitWebhook.WebhookEvent event) {
        var meetingId = extractMeetingId(event.getRoom().getName());
        if (meetingId == null) return;

        closeStaleMeetingLogsUseCase.execute(new CloseStaleMeetingLogsCommand(meetingId));
    }

    private void handleEgressStarted(LivekitWebhook.WebhookEvent event) {
        var egressInfo = event.getEgressInfo();
        if (egressInfo == null || egressInfo.getEgressId().isBlank()) {
            log.warn("egress_started webhook missing egress info");
            return;
        }

        activateRecordingUseCase.execute(new ActivateRecordingCommand(egressInfo.getEgressId()));
    }

    private void handleEgressEnded(LivekitWebhook.WebhookEvent event) {
        var egressInfo = event.getEgressInfo();
        if (egressInfo == null || egressInfo.getEgressId().isBlank()) {
            log.warn("egress_ended webhook missing egress info");
            return;
        }

        String errorMessage = egressInfo.getError();
        boolean hasFileOutput = egressInfo.getFileResultsCount() > 0;
        if (!errorMessage.isBlank() || !hasFileOutput) {
            finalizeRecordingUseCase.execute(new FinalizeRecordingCommand(
                    egressInfo.getEgressId(),
                    false,
                    null,
                    null,
                    !errorMessage.isBlank()
                            ? errorMessage
                            : "LiveKit egress ended without file output",
                    0,
                    0L));
            return;
        }

        var fileResult = egressInfo.getFileResults(0);
        finalizeRecordingUseCase.execute(new FinalizeRecordingCommand(
                egressInfo.getEgressId(),
                true,
                toPublicUrl(fileResult.getLocation()),
                fileResult.getFilename().isBlank()
                        ? fileResult.getLocation()
                        : fileResult.getFilename(),
                null,
                Math.toIntExact(fileResult.getDuration() / 1_000_000_000L),
                fileResult.getSize()));
    }

    /**
     * Extracts the meeting UUID from a LiveKit room name.
     *
     * <p>Room names are formatted as {@code "meeting-{uuid}"} by
     * {@link LiveKitRoomName#fromMeetingId}.
     *
     * @return the parsed UUID, or {@code null} if the room name is not a managed meeting room
     */
    private UUID extractMeetingId(String roomName) {
        if (roomName == null || !roomName.startsWith(ROOM_NAME_PREFIX)) {
            log.debug("Ignoring webhook for non-meeting room: {}", roomName);
            return null;
        }
        String uuidPart = roomName.substring(ROOM_NAME_PREFIX.length());
        try {
            return UUID.fromString(uuidPart);
        } catch (IllegalArgumentException e) {
            log.warn(
                    "Could not parse meeting UUID from room name '{}': {}",
                    roomName,
                    e.getMessage());
            return null;
        }
    }

    /**
     * Rewrites an internal egress file URL to the public-facing storage endpoint.
     *
     * <p>LiveKit egress reports file locations using the internal Docker DNS endpoint
     * (e.g. {@code http://rustfs:9000/...}). Browsers cannot resolve internal hostnames,
     * so this method replaces the egress endpoint prefix with the public endpoint configured
     * in {@code app.livekit.recording.endpoint}.
     */
    private String toPublicUrl(String egressUrl) {
        if (egressUrl == null) return null;
        String egressEndpoint = liveKitProperties.getRecording().getEgressEndpoint();
        String publicEndpoint = liveKitProperties.getRecording().getEndpoint();
        if (egressUrl.startsWith(egressEndpoint)) {
            return publicEndpoint + egressUrl.substring(egressEndpoint.length());
        }
        return egressUrl;
    }
}
