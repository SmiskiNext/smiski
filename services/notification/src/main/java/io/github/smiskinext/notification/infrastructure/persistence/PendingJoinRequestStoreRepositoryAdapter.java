package io.github.smiskinext.notification.infrastructure.persistence;

import io.github.smiskinext.notification.domain.model.PendingJoinRequest;
import io.github.smiskinext.notification.domain.port.PendingJoinRequestStore;
import io.github.smiskinext.notification.infrastructure.persistence.model.PendingJoinRequestData;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Redis-backed {@link PendingJoinRequestStore}.
 *
 * <p>Layout per meeting:
 *
 * <ul>
 *   <li>{@code ZSET join_notification:{meetingId}} — request ids scored by {@code expiresAt} epoch
 *       ms, used to enumerate a meeting's pending requests
 *   <li>{@code STRING join_notification_meta:{requestId}} — request metadata (JSON)
 * </ul>
 *
 * <p>Both keys receive a TTL aligned to the join request lifetime (5 minutes) plus a small buffer on
 * the ZSET so it is not evicted before its members. Reads drop entries whose {@code expiresAt} has
 * elapsed and any whose metadata has already been evicted, so replay reflects only unexpired
 * requests.
 */
@Repository
public class PendingJoinRequestStoreRepositoryAdapter implements PendingJoinRequestStore {

    private static final Duration ENTRY_TTL = Duration.ofMinutes(5);
    private static final Duration ZSET_TTL_BUFFER = Duration.ofSeconds(120);

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, PendingJoinRequestData> pendingJoinRequestRedisTemplate;

    public PendingJoinRequestStoreRepositoryAdapter(
            StringRedisTemplate stringRedisTemplate,
            RedisTemplate<String, PendingJoinRequestData> pendingJoinRequestRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.pendingJoinRequestRedisTemplate = pendingJoinRequestRedisTemplate;
    }

    @Override
    public void upsert(PendingJoinRequest request) {
        String meetingId = request.meetingId().toString();
        String requestId = request.joinRequestId().toString();

        String queueKey = queueKey(meetingId);
        String metaKey = metaKey(requestId);

        stringRedisTemplate
                .opsForZSet()
                .add(queueKey, requestId, request.expiresAt().toEpochMilli());
        stringRedisTemplate.expire(queueKey, ENTRY_TTL.plus(ZSET_TTL_BUFFER));

        pendingJoinRequestRedisTemplate
                .opsForValue()
                .set(metaKey, PendingJoinRequestData.from(request), ENTRY_TTL);
    }

    @Override
    public List<PendingJoinRequest> findPendingByMeetingId(UUID meetingId) {
        String queueKey = queueKey(meetingId.toString());
        Set<String> requestIds = stringRedisTemplate.opsForZSet().range(queueKey, 0, -1);
        if (requestIds == null || requestIds.isEmpty()) {
            return List.of();
        }

        long nowMs = Instant.now().toEpochMilli();
        List<String> metaKeys = requestIds.stream().map(this::metaKey).collect(Collectors.toList());
        List<PendingJoinRequestData> dataList =
                pendingJoinRequestRedisTemplate.opsForValue().multiGet(metaKeys);
        if (dataList == null) {
            return List.of();
        }

        List<PendingJoinRequest> result = new ArrayList<>();
        for (PendingJoinRequestData data : dataList) {
            if (data == null) {
                continue;
            }
            PendingJoinRequest request = data.toDomain();
            if (request.expiresAt().toEpochMilli() > nowMs) {
                result.add(request);
            }
        }
        result.sort(Comparator.comparing(PendingJoinRequest::expiresAt));
        return result;
    }

    private String queueKey(String meetingId) {
        return "join_notification:" + meetingId;
    }

    private String metaKey(String requestId) {
        return "join_notification_meta:" + requestId;
    }
}
