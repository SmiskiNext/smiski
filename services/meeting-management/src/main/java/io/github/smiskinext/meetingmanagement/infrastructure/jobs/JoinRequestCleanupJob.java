package io.github.smiskinext.meetingmanagement.infrastructure.jobs;

import io.github.smiskinext.meetingmanagement.domain.event.JoinRequestExpiredEvent;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestResult;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestStatus;
import io.github.smiskinext.meetingmanagement.domain.port.JoinRequestRepository;
import io.github.smiskinext.meetingmanagement.domain.port.JoinRequestResultStore;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled job that expires join requests past their TTL.
 *
 * <p>Runs every 60 seconds, scans all {@code join_request:*} sorted sets for entries with score
 * (expiresAt) less than current time, marks them as {@code EXPIRED}, publishes
 * {@link JoinRequestExpiredEvent} via the Outbox pattern, and removes from queue.
 *
 * <p>Uses {@code SCAN} instead of {@code KEYS} to avoid blocking the Redis event loop on large
 * key spaces. Empty ZSETs are deleted after processing to prevent orphan key accumulation.
 *
 * <p>Annotated with {@code @Transactional} to create the required transaction context for
 * {@code OutboxEventListener} ({@code @TransactionalEventListener(AFTER_COMMIT)}) to capture
 * domain events published within this method.
 */
@Component
public class JoinRequestCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(JoinRequestCleanupJob.class);

    private final StringRedisTemplate redisTemplate;
    private final JoinRequestRepository joinRequestRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final JoinRequestResultStore joinRequestResultStore;

    public JoinRequestCleanupJob(
            StringRedisTemplate redisTemplate,
            JoinRequestRepository joinRequestRepository,
            ApplicationEventPublisher applicationEventPublisher,
            JoinRequestResultStore joinRequestResultStore) {
        this.redisTemplate = redisTemplate;
        this.joinRequestRepository = joinRequestRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.joinRequestResultStore = joinRequestResultStore;
    }

    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void cleanupExpiredRequests() {
        long now = Instant.now().toEpochMilli();

        ScanOptions scanOptions =
                ScanOptions.scanOptions().match("join_request:*").count(100).build();

        int expiredCount = 0;
        try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                String queueKey = cursor.next();

                if (queueKey.contains("_meta:") || queueKey.contains("_device:")) {
                    continue;
                }

                String meetingIdStr = queueKey.substring("join_request:".length());
                UUID meetingId;
                try {
                    meetingId = UUID.fromString(meetingIdStr);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid meetingId in queue key: {}", queueKey);
                    continue;
                }

                Set<String> expiredRequestIds =
                        redisTemplate.opsForZSet().rangeByScore(queueKey, 0, now);
                if (expiredRequestIds == null || expiredRequestIds.isEmpty()) {
                    cleanupEmptyZset(queueKey);
                    continue;
                }

                for (String requestIdStr : expiredRequestIds) {
                    UUID requestId = UUID.fromString(requestIdStr);

                    joinRequestRepository.updateStatus(requestId, JoinRequestStatus.EXPIRED);

                    var expiredEvent = new JoinRequestExpiredEvent(
                            UUID.randomUUID(), meetingId, requestId, Instant.now());
                    applicationEventPublisher.publishEvent(expiredEvent);

                    joinRequestResultStore.save(JoinRequestResult.expired(requestId));

                    joinRequestRepository.removeFromQueue(meetingId, requestId);

                    expiredCount++;
                }

                cleanupEmptyZset(queueKey);
            }
        }

        if (expiredCount > 0) {
            log.info("Expired {} join requests", expiredCount);
        }
    }

    private void cleanupEmptyZset(String queueKey) {
        Long size = redisTemplate.opsForZSet().zCard(queueKey);
        if (size != null && size == 0) {
            redisTemplate.delete(queueKey);
        }
    }
}
