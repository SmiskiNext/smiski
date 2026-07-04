package io.github.smiskinext.meetingmanagement.infrastructure.sse;

import io.cloudevents.CloudEvent;
import io.github.smiskinext.meetingmanagement.domain.event.JoinRequestApprovedEvent;
import io.github.smiskinext.meetingmanagement.domain.event.JoinRequestCreatedEvent;
import io.github.smiskinext.meetingmanagement.domain.event.JoinRequestDeniedEvent;
import io.github.smiskinext.meetingmanagement.domain.event.JoinRequestExpiredEvent;
import io.github.smiskinext.meetingmanagement.domain.event.ParticipantKickedEvent;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequest;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestResult;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestStatus;
import io.github.smiskinext.meetingmanagement.domain.port.JoinRequestRepository;
import io.github.smiskinext.meetingmanagement.domain.port.JoinRequestResultStore;
import io.github.smiskinext.meetingmanagement.infrastructure.config.SseProperties;
import io.github.phunguy65.zms.meetingmanagement.infrastructure.sse.model.*;
import io.github.smiskinext.meetingmanagement.infrastructure.sse.model.*;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Manages SSE connections for meeting hosts and guests, broadcasting join request lifecycle events.
 *
 * <p>Maintains two emitter registries:
 *
 * <ul>
 *   <li>{@code hostEmittersByMeeting} — meeting-scoped emitters for hosts watching new join
 *       requests
 *   <li>{@code guestEmittersByRequest} — request-scoped emitters for guests waiting on approval
 * </ul>
 *
 * <p>Events arrive through two parallel channels:
 *
 * <ol>
 *   <li><strong>In-process</strong> via Spring's {@code AFTER_COMMIT} transactional listener —
 *       guarantees same-instance delivery without depending on Kafka latency.
 *   <li><strong>Kafka</strong> CloudEvent listeners — fan-out to other instances so any service
 *       replica with a live emitter can push the event to its connected client.
 * </ol>
 *
 * <p>Both channels feed the same dispatch methods. Duplicate sends to a single guest emitter are
 * harmless because the emitter is removed from the registry as soon as the first send completes
 * the stream.
 *
 * <p>Guest streams are protected against the host-acted-before-subscribe race by replaying any
 * persisted terminal {@link JoinRequestResult} from {@link JoinRequestResultStore} immediately
 * after the emitter registers. Host streams replay the current pending join requests on
 * subscription so a host that opens the waiting room after a request was created still sees it.
 * Both stream types receive periodic heartbeat comments to keep idle proxies from closing the
 * connection.
 *
 * <p>Thread-safe: uses {@link ConcurrentHashMap} + {@link CopyOnWriteArrayList} for emitter
 * storage.
 */
@Component
public class MeetingSseManager {

    private static final Logger log = LoggerFactory.getLogger(MeetingSseManager.class);

    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> hostEmittersByMeeting =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<UUID, SseEmitter> guestEmittersByRequest =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<SseEmitter, ScheduledFuture<?>> heartbeatTasks =
            new ConcurrentHashMap<>();

    private final SseProperties sseProperties;
    private final ObjectMapper objectMapper;
    private final JoinRequestResultStore joinRequestResultStore;
    private final JoinRequestRepository joinRequestRepository;
    private final ScheduledExecutorService heartbeatScheduler;

    public MeetingSseManager(
            SseProperties sseProperties,
            ObjectMapper objectMapper,
            JoinRequestResultStore joinRequestResultStore,
            JoinRequestRepository joinRequestRepository) {
        this.sseProperties = sseProperties;
        this.objectMapper = objectMapper;
        this.joinRequestResultStore = joinRequestResultStore;
        this.joinRequestRepository = joinRequestRepository;
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    public SseEmitter subscribeHost(UUID meetingId, UUID userId) {
        SseEmitter emitter = new SseEmitter(sseProperties.getTimeoutMs());

        hostEmittersByMeeting
                .computeIfAbsent(meetingId, k -> new CopyOnWriteArrayList<>())
                .add(emitter);

        emitter.onCompletion(() -> removeHostEmitter(meetingId, emitter));
        emitter.onTimeout(() -> {
            log.debug("SSE timeout for host meeting={} user={}", meetingId, userId);
            removeHostEmitter(meetingId, emitter);
        });
        emitter.onError(e -> {
            log.warn(
                    "SSE error for host meeting={} user={}: {}", meetingId, userId, e.getMessage());
            removeHostEmitter(meetingId, emitter);
        });

        sendHeartbeat(emitter);
        scheduleHeartbeat(emitter);

        log.debug("Host {} subscribed to SSE for meeting {}", userId, meetingId);

        replayPendingJoinRequests(meetingId, emitter);
        return emitter;
    }

    public SseEmitter subscribeGuest(UUID requestId) {
        SseEmitter emitter = new SseEmitter(sseProperties.getJoinRequestTimeoutMs());

        guestEmittersByRequest.put(requestId, emitter);

        emitter.onCompletion(() -> cleanupGuest(requestId, emitter));
        emitter.onTimeout(() -> {
            log.debug("SSE timeout for guest request={}", requestId);
            cleanupGuest(requestId, emitter);
        });
        emitter.onError(e -> {
            log.warn("SSE error for guest request={}: {}", requestId, e.getMessage());
            cleanupGuest(requestId, emitter);
        });

        sendHeartbeat(emitter);
        scheduleHeartbeat(emitter);

        log.debug("Guest subscribed to SSE for join request {}", requestId);

        replayTerminalResultIfPresent(requestId);
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJoinRequestCreatedInProcess(JoinRequestCreatedEvent event) {
        dispatchJoinRequestCreated(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJoinRequestApprovedInProcess(JoinRequestApprovedEvent event) {
        dispatchJoinRequestApproved(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJoinRequestDeniedInProcess(JoinRequestDeniedEvent event) {
        dispatchJoinRequestDenied(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJoinRequestExpiredInProcess(JoinRequestExpiredEvent event) {
        dispatchJoinRequestExpired(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onParticipantKickedInProcess(ParticipantKickedEvent event) {
        dispatchParticipantKicked(event);
    }

    @KafkaListener(
            topics = "meeting-management.join-request.created",
            containerFactory = "cloudEventKafkaListenerContainerFactory")
    public void onJoinRequestCreated(CloudEvent cloudEvent) {
        JoinRequestCreatedEvent event = deserialize(cloudEvent, JoinRequestCreatedEvent.class);
        if (event == null) return;
        dispatchJoinRequestCreated(event);
    }

    @KafkaListener(
            topics = "meeting-management.join-request.approved",
            containerFactory = "cloudEventKafkaListenerContainerFactory")
    public void onJoinRequestApproved(CloudEvent cloudEvent) {
        JoinRequestApprovedEvent event = deserialize(cloudEvent, JoinRequestApprovedEvent.class);
        if (event == null) return;
        dispatchJoinRequestApproved(event);
    }

    @KafkaListener(
            topics = "meeting-management.join-request.denied",
            containerFactory = "cloudEventKafkaListenerContainerFactory")
    public void onJoinRequestDenied(CloudEvent cloudEvent) {
        JoinRequestDeniedEvent event = deserialize(cloudEvent, JoinRequestDeniedEvent.class);
        if (event == null) return;
        dispatchJoinRequestDenied(event);
    }

    @KafkaListener(
            topics = "meeting-management.join-request.expired",
            containerFactory = "cloudEventKafkaListenerContainerFactory")
    public void onJoinRequestExpired(CloudEvent cloudEvent) {
        JoinRequestExpiredEvent event = deserialize(cloudEvent, JoinRequestExpiredEvent.class);
        if (event == null) return;
        dispatchJoinRequestExpired(event);
    }

    @KafkaListener(
            topics = "meeting-management.participant.kicked",
            containerFactory = "cloudEventKafkaListenerContainerFactory")
    public void onParticipantKicked(CloudEvent cloudEvent) {
        ParticipantKickedEvent event = deserialize(cloudEvent, ParticipantKickedEvent.class);
        if (event == null) return;
        dispatchParticipantKicked(event);
    }

    private void dispatchJoinRequestCreated(JoinRequestCreatedEvent event) {
        UUID meetingId = event.meetingId();
        SseEventData sseData = new JoinRequestCreatedData(
                event.joinRequestId().toString(), meetingId.toString(), event.displayName());
        pushToHostEmitters(meetingId, "join_request_created", sseData);
    }

    private void dispatchJoinRequestApproved(JoinRequestApprovedEvent event) {
        UUID requestId = event.joinRequestId();
        SseEventData sseData = new JoinRequestApprovedData(
                requestId.toString(),
                JoinRequestStatus.APPROVED.name(),
                event.liveKitToken(),
                event.roomName());
        sendToGuestAndComplete(requestId, "join_request_approved", sseData);
    }

    private void dispatchJoinRequestDenied(JoinRequestDeniedEvent event) {
        UUID requestId = event.joinRequestId();
        SseEventData sseData =
                new JoinRequestDeniedData(requestId.toString(), JoinRequestStatus.DENIED.name());
        sendToGuestAndComplete(requestId, "join_request_denied", sseData);
    }

    private void dispatchJoinRequestExpired(JoinRequestExpiredEvent event) {
        UUID meetingId = event.meetingId();
        UUID requestId = event.joinRequestId();
        SseEventData sseData =
                new JoinRequestExpiredData(requestId.toString(), JoinRequestStatus.EXPIRED.name());
        pushToHostEmitters(meetingId, "join_request_expired", sseData);
        sendToGuestAndComplete(requestId, "join_request_expired", sseData);
    }

    private void dispatchParticipantKicked(ParticipantKickedEvent event) {
        UUID meetingId = event.meetingId();
        SseEventData sseData = new ParticipantKickedData(
                meetingId.toString(),
                event.kickedUserId() != null ? event.kickedUserId().toString() : null,
                event.kickedDisplayName() != null ? event.kickedDisplayName() : "unknown");
        pushToHostEmitters(meetingId, "participant_kicked", sseData);
    }

    private void pushToHostEmitters(UUID meetingId, String eventName, SseEventData data) {
        CopyOnWriteArrayList<SseEmitter> emitters = hostEmittersByMeeting.get(meetingId);
        if (emitters == null || emitters.isEmpty()) return;

        List<SseEmitter> dead = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                log.debug("Dead host emitter for meeting={}: {}", meetingId, e.getMessage());
                dead.add(emitter);
            }
        }
        dead.forEach(emitter -> removeHostEmitter(meetingId, emitter));
    }

    private void sendToGuestAndComplete(UUID requestId, String eventName, SseEventData data) {
        SseEmitter emitter = guestEmittersByRequest.remove(requestId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            emitter.complete();
        } catch (IOException e) {
            log.debug("Dead guest emitter for request={}: {}", requestId, e.getMessage());
        } finally {
            cancelHeartbeat(emitter);
            joinRequestResultStore.delete(requestId);
        }
    }

    private void replayTerminalResultIfPresent(UUID requestId) {
        joinRequestResultStore.findByRequestId(requestId).ifPresent(result -> {
            log.debug(
                    "Replaying terminal result for late-subscribing guest request={} status={}",
                    requestId,
                    result.status());
            SseEventData data = toSseEventData(result);
            String eventName =
                    switch (result.status()) {
                        case APPROVED -> "join_request_approved";
                        case DENIED -> "join_request_denied";
                        case EXPIRED -> "join_request_expired";
                        default -> null;
                    };
            if (eventName == null || data == null) return;
            sendToGuestAndComplete(requestId, eventName, data);
        });
    }

    private void replayPendingJoinRequests(UUID meetingId, SseEmitter emitter) {
        List<JoinRequest> pending;
        try {
            pending = joinRequestRepository.findPendingByMeetingId(meetingId);
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to load pending join requests for meeting={} on subscribe: {}",
                    meetingId,
                    e.getMessage());
            return;
        }

        for (JoinRequest joinRequest : pending) {
            JoinRequestCreatedData data = new JoinRequestCreatedData(
                    joinRequest.getId().value().toString(),
                    meetingId.toString(),
                    joinRequest.getDisplayName());
            try {
                emitter.send(SseEmitter.event().name("join_request_created").data(data));
            } catch (IOException e) {
                log.debug(
                        "Dead host emitter while replaying pending requests for meeting={}: {}",
                        meetingId,
                        e.getMessage());
                removeHostEmitter(meetingId, emitter);
                return;
            }
        }
    }

    private SseEventData toSseEventData(JoinRequestResult result) {
        return switch (result.status()) {
            case APPROVED ->
                new JoinRequestApprovedData(
                        result.requestId().toString(),
                        JoinRequestStatus.APPROVED.name(),
                        result.liveKitToken(),
                        result.roomName());
            case DENIED ->
                new JoinRequestDeniedData(
                        result.requestId().toString(), JoinRequestStatus.DENIED.name());
            case EXPIRED ->
                new JoinRequestExpiredData(
                        result.requestId().toString(), JoinRequestStatus.EXPIRED.name());
            default -> null;
        };
    }

    private void sendHeartbeat(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("ka"));
        } catch (IOException e) {
            log.debug("Heartbeat send failed: {}", e.getMessage());
        }
    }

    private void scheduleHeartbeat(SseEmitter emitter) {
        ScheduledFuture<?> task = heartbeatScheduler.scheduleAtFixedRate(
                () -> sendHeartbeat(emitter),
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
        heartbeatTasks.put(emitter, task);
    }

    private void cancelHeartbeat(SseEmitter emitter) {
        ScheduledFuture<?> task = heartbeatTasks.remove(emitter);
        if (task != null) {
            task.cancel(false);
        }
    }

    private void cleanupGuest(UUID requestId, SseEmitter emitter) {
        guestEmittersByRequest.remove(requestId, emitter);
        cancelHeartbeat(emitter);
    }

    private void removeHostEmitter(UUID meetingId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = hostEmittersByMeeting.get(meetingId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                hostEmittersByMeeting.remove(meetingId);
            }
        }
        cancelHeartbeat(emitter);
    }

    private <T> T deserialize(CloudEvent cloudEvent, Class<T> type) {
        if (cloudEvent.getData() == null) {
            log.warn("Received CloudEvent with no data payload: {}", cloudEvent.getId());
            return null;
        }
        try {
            byte[] data = cloudEvent.getData().toBytes();
            return objectMapper.readValue(data, type);
        } catch (JacksonException e) {
            log.error(
                    "Failed to deserialize CloudEvent {} to {}: {}",
                    cloudEvent.getId(),
                    type.getSimpleName(),
                    e.getMessage());
            return null;
        }
    }

    @PreDestroy
    void shutdown() {
        heartbeatScheduler.shutdownNow();
    }
}
