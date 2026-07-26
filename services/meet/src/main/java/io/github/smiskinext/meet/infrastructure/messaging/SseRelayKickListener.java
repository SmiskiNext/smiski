package io.github.smiskinext.meet.infrastructure.messaging;

import io.github.smiskinext.meet.domain.event.SseTriggeringEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxRelay;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Accelerates host-facing SSE delivery for {@link SseTriggeringEvent} instances by running the
 * service-local {@link OutboxRelay} immediately after the originating transaction commits, instead
 * of waiting for the scheduled outbox poll.
 *
 * <p>The outbox row is still enqueued pre-commit by the shared outbox listener; this listener runs
 * at {@link TransactionPhase#AFTER_COMMIT}, so the row is guaranteed visible and durable before the
 * relay attempts to publish it. The relay is dispatched on the auto-configured, virtual-thread-backed
 * {@code applicationTaskExecutor} so the request thread returns without blocking on the transport
 * send.
 *
 * <p>The relay reuses the same {@code FOR UPDATE SKIP LOCKED} claiming and {@code published_at}
 * marking as the scheduled poll, so the immediate kick and the poll never double-publish a row. Any
 * dispatch rejection or relay failure is logged and swallowed: the row stays unpublished and the
 * unchanged scheduled poll republishes it on its next run.
 */
@Component
public class SseRelayKickListener {

    private static final Logger log = LoggerFactory.getLogger(SseRelayKickListener.class);

    private final OutboxRelay outboxRelay;
    private final Executor taskExecutor;

    public SseRelayKickListener(
            OutboxRelay outboxRelay, @Qualifier("applicationTaskExecutor") Executor taskExecutor) {
        this.outboxRelay = outboxRelay;
        this.taskExecutor = taskExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSseTriggeringEvent(SseTriggeringEvent event) {
        try {
            taskExecutor.execute(this::relayQuietly);
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to dispatch immediate outbox relay; scheduled poll will publish: {}",
                    e.getMessage());
        }
    }

    private void relayQuietly() {
        try {
            outboxRelay.relay();
        } catch (RuntimeException e) {
            log.warn(
                    "Immediate outbox relay failed; scheduled poll will retry: {}", e.getMessage());
        }
    }
}
