package io.github.smiskinext.notification.application.sse;

import io.github.smiskinext.notification.domain.model.JoinDecision;
import io.github.smiskinext.notification.domain.model.PendingJoinRequest;
import io.github.smiskinext.notification.domain.port.JoinDecisionStore;
import io.github.smiskinext.notification.domain.port.PendingJoinRequestStore;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Registry of SSE emitters with a single shared daemon heartbeat scheduler, holding two independent
 * registries that share the heartbeat and timeout configuration:
 *
 * <ul>
 *   <li>host streams keyed by meeting id — the Kafka consumer pushes consumed join requests to the
 *       emitters held locally for that meeting;
 *   <li>requester streams keyed by join request id — the resolved-event consumer pushes the host's
 *       accept/decline outcome to the emitters held locally for that request id.
 * </ul>
 *
 * <p>Because each replica consumes every event under its own consumer group, a replica pushes to
 * whatever emitters it holds. On subscribe, host streams replay the meeting's currently pending
 * requests from {@link PendingJoinRequestStore} and requester streams replay a decision already
 * recorded in {@link JoinDecisionStore}, so a subscriber connecting after the fact still sees it.
 *
 * <p>Emitter completion, timeout, and error all remove the emitter and cancel its heartbeat task so
 * no resources leak. Thread-safe via {@link ConcurrentHashMap} + {@link CopyOnWriteArrayList}.
 */
@Component
public class SseConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(SseConnectionManager.class);

    private static final String EVENT_JOIN_REQUEST_CREATED = "join_request_created";
    private static final String EVENT_JOIN_REQUEST_APPROVED = "join_request_approved";
    private static final String EVENT_JOIN_REQUEST_DENIED = "join_request_denied";
    private static final String HEARTBEAT_COMMENT = "ka";

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByMeeting =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByRequest =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SseEmitter, ScheduledFuture<?>> heartbeatTasks =
            new ConcurrentHashMap<>();

    private final SseProperties sseProperties;
    private final PendingJoinRequestStore pendingJoinRequestStore;
    private final JoinDecisionStore joinDecisionStore;
    private final ScheduledExecutorService heartbeatScheduler;

    public SseConnectionManager(
            SseProperties sseProperties,
            PendingJoinRequestStore pendingJoinRequestStore,
            JoinDecisionStore joinDecisionStore) {
        this.sseProperties = sseProperties;
        this.pendingJoinRequestStore = pendingJoinRequestStore;
        this.joinDecisionStore = joinDecisionStore;
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Registers a new host emitter for a meeting, sends an initial heartbeat, schedules periodic
     * heartbeats, and replays the meeting's currently pending join requests.
     *
     * @param meetingId the meeting to subscribe to
     * @return the emitter to return from the SSE controller
     */
    public SseEmitter subscribe(UUID meetingId) {
        SseEmitter emitter = new SseEmitter(sseProperties.getHostStreamTimeoutMs());

        emittersByMeeting
                .computeIfAbsent(meetingId, key -> new CopyOnWriteArrayList<>())
                .add(emitter);

        emitter.onCompletion(() -> remove(meetingId, emitter));
        emitter.onTimeout(() -> remove(meetingId, emitter));
        emitter.onError(error -> remove(meetingId, emitter));

        sendHeartbeat(emitter);
        scheduleHeartbeat(emitter);

        replayPending(meetingId, emitter);
        return emitter;
    }

    /**
     * Registers a new requester emitter for a join request, sends an initial heartbeat, schedules
     * periodic heartbeats, and replays a decision already recorded for that request id.
     *
     * @param requestId the join request whose outcome to subscribe to
     * @return the emitter to return from the SSE controller
     */
    public SseEmitter subscribeRequest(UUID requestId) {
        SseEmitter emitter = new SseEmitter(sseProperties.getHostStreamTimeoutMs());

        emittersByRequest
                .computeIfAbsent(requestId, key -> new CopyOnWriteArrayList<>())
                .add(emitter);

        emitter.onCompletion(() -> removeRequest(requestId, emitter));
        emitter.onTimeout(() -> removeRequest(requestId, emitter));
        emitter.onError(error -> removeRequest(requestId, emitter));

        sendHeartbeat(emitter);
        scheduleHeartbeat(emitter);

        replayDecision(requestId, emitter);
        return emitter;
    }

    /**
     * Pushes a {@code join_request_created} event to every host emitter held locally for the meeting.
     *
     * @param meetingId the target meeting
     * @param data      the join request payload
     */
    public void pushJoinRequestCreated(UUID meetingId, JoinRequestCreatedData data) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByMeeting.get(meetingId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            send(meetingId, emitter, data);
        }
    }

    /**
     * Pushes the host's decision to every requester emitter held locally for the request id, as a
     * {@code join_request_approved} or {@code join_request_denied} event.
     *
     * @param requestId the target join request
     * @param decision  the recorded decision to deliver
     */
    public void pushJoinResolved(UUID requestId, JoinDecision decision) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByRequest.get(requestId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            sendDecision(requestId, emitter, decision);
        }
    }

    private void replayPending(UUID meetingId, SseEmitter emitter) {
        List<PendingJoinRequest> pending;
        try {
            pending = pendingJoinRequestStore.findPendingByMeetingId(meetingId);
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to load pending join requests for meeting={} on subscribe: {}",
                    meetingId,
                    e.getMessage());
            return;
        }
        for (PendingJoinRequest request : pending) {
            JoinRequestCreatedData data = new JoinRequestCreatedData(
                    request.joinRequestId().toString(),
                    request.accountId(),
                    request.displayName(),
                    request.avatarUrl());
            send(meetingId, emitter, data);
        }
    }

    private void replayDecision(UUID requestId, SseEmitter emitter) {
        Optional<JoinDecision> decision;
        try {
            decision = joinDecisionStore.findByRequestId(requestId);
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to load recorded decision for request={} on subscribe: {}",
                    requestId,
                    e.getMessage());
            return;
        }
        decision.ifPresent(value -> sendDecision(requestId, emitter, value));
    }

    private void send(UUID meetingId, SseEmitter emitter, JoinRequestCreatedData data) {
        try {
            emitter.send(SseEmitter.event().name(EVENT_JOIN_REQUEST_CREATED).data(data));
        } catch (IOException | IllegalStateException e) {
            log.debug("Dead host emitter for meeting={}: {}", meetingId, e.getMessage());
            remove(meetingId, emitter);
        }
    }

    private void sendDecision(UUID requestId, SseEmitter emitter, JoinDecision decision) {
        SseEmitter.SseEventBuilder event =
                switch (decision.status()) {
                    case APPROVED ->
                        SseEmitter.event()
                                .name(EVENT_JOIN_REQUEST_APPROVED)
                                .data(new JoinRequestApprovedData(
                                        decision.token(), decision.roomName()));
                    case DENIED ->
                        SseEmitter.event()
                                .name(EVENT_JOIN_REQUEST_DENIED)
                                .data(new JoinRequestDeniedData(decision.reason()));
                };
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            log.debug("Dead requester emitter for request={}: {}", requestId, e.getMessage());
            removeRequest(requestId, emitter);
        }
    }

    private void sendHeartbeat(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment(HEARTBEAT_COMMENT));
        } catch (IOException | IllegalStateException e) {
            log.debug("Heartbeat send failed: {}", e.getMessage());
        }
    }

    private void scheduleHeartbeat(SseEmitter emitter) {
        long interval = sseProperties.getHeartbeatIntervalSeconds();
        ScheduledFuture<?> task = heartbeatScheduler.scheduleAtFixedRate(
                () -> sendHeartbeat(emitter), interval, interval, TimeUnit.SECONDS);
        heartbeatTasks.put(emitter, task);
    }

    private void cancelHeartbeat(SseEmitter emitter) {
        ScheduledFuture<?> task = heartbeatTasks.remove(emitter);
        if (task != null) {
            task.cancel(false);
        }
    }

    private void remove(UUID meetingId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByMeeting.get(meetingId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersByMeeting.remove(meetingId);
            }
        }
        cancelHeartbeat(emitter);
    }

    private void removeRequest(UUID requestId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByRequest.get(requestId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersByRequest.remove(requestId);
            }
        }
        cancelHeartbeat(emitter);
    }

    @PreDestroy
    void shutdown() {
        heartbeatScheduler.shutdownNow();
    }
}
