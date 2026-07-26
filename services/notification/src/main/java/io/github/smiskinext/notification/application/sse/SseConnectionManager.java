package io.github.smiskinext.notification.application.sse;

import io.github.smiskinext.notification.domain.model.PendingJoinRequest;
import io.github.smiskinext.notification.domain.port.PendingJoinRequestStore;
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
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Registry of host SSE emitters keyed by meeting, with a single shared daemon heartbeat scheduler.
 *
 * <p>The controller subscribes a host to a meeting stream; the Kafka consumer pushes consumed join
 * requests to the emitters held locally for that meeting. Because each notification replica consumes
 * every join event under its own consumer group, a replica pushes to whatever emitters it holds.
 *
 * <p>On subscribe, the meeting's currently pending requests are replayed from
 * {@link PendingJoinRequestStore} so a host connecting after requests arrived still sees them.
 *
 * <p>Emitter completion, timeout, and error all remove the emitter and cancel its heartbeat task so
 * no resources leak. Thread-safe via {@link ConcurrentHashMap} + {@link CopyOnWriteArrayList}.
 */
@Component
public class SseConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(SseConnectionManager.class);

    private static final String EVENT_JOIN_REQUEST_CREATED = "join_request_created";
    private static final String HEARTBEAT_COMMENT = "ka";

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByMeeting =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SseEmitter, ScheduledFuture<?>> heartbeatTasks =
            new ConcurrentHashMap<>();

    private final SseProperties sseProperties;
    private final PendingJoinRequestStore pendingJoinRequestStore;
    private final ScheduledExecutorService heartbeatScheduler;

    public SseConnectionManager(
            SseProperties sseProperties, PendingJoinRequestStore pendingJoinRequestStore) {
        this.sseProperties = sseProperties;
        this.pendingJoinRequestStore = pendingJoinRequestStore;
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
     * Pushes a {@code join_request_created} event to every emitter held locally for the meeting.
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

    private void send(UUID meetingId, SseEmitter emitter, JoinRequestCreatedData data) {
        try {
            emitter.send(SseEmitter.event().name(EVENT_JOIN_REQUEST_CREATED).data(data));
        } catch (IOException | IllegalStateException e) {
            log.debug("Dead host emitter for meeting={}: {}", meetingId, e.getMessage());
            remove(meetingId, emitter);
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

    @PreDestroy
    void shutdown() {
        heartbeatScheduler.shutdownNow();
    }
}
