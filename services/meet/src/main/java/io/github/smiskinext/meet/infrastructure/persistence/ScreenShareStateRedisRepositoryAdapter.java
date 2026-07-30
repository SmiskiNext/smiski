package io.github.smiskinext.meet.infrastructure.persistence;

import io.github.smiskinext.meet.domain.port.ScreenShareStateRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Redis-backed repository for current screen-share state.
 *
 * <p>Data structure: {@code ZSET screen_share:{meetingId}} — members are account ids currently
 * sharing, scored by the epoch-millis timestamp sharing started. The whole set carries a
 * safety-net TTL (refreshed on every {@link #markSharing}) so a missed {@code track_unpublished}
 * webhook cannot leave a stale "sharing" flag forever; {@link
 * io.github.smiskinext.meet.application.service.LiveKitWebhookProcessingApplicationService} also
 * clears an account's entry explicitly when its participation session ends.
 */
@Repository
public class ScreenShareStateRedisRepositoryAdapter implements ScreenShareStateRepository {

    private static final Duration SET_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public ScreenShareStateRedisRepositoryAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean isSharing(UUID meetingId, String accountId) {
        Double score = redisTemplate.opsForZSet().score(key(meetingId), accountId);
        return score != null;
    }

    @Override
    public void markSharing(UUID meetingId, String accountId, Instant startedAt) {
        String key = key(meetingId);
        redisTemplate.opsForZSet().add(key, accountId, startedAt.toEpochMilli());
        redisTemplate.expire(key, SET_TTL);
    }

    @Override
    public void clearSharing(UUID meetingId, String accountId) {
        redisTemplate.opsForZSet().remove(key(meetingId), accountId);
    }

    @Override
    public Set<String> findSharingAccountIds(UUID meetingId) {
        Set<String> members = redisTemplate.opsForZSet().range(key(meetingId), 0, -1);
        return members == null ? Set.of() : members;
    }

    private String key(UUID meetingId) {
        return "screen_share:" + meetingId;
    }
}
