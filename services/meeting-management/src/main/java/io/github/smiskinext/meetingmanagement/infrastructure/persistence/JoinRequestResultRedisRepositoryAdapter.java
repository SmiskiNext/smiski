package io.github.smiskinext.meetingmanagement.infrastructure.persistence;

import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestResult;
import io.github.smiskinext.meetingmanagement.domain.port.JoinRequestResultStore;
import io.github.smiskinext.meetingmanagement.infrastructure.config.SseProperties;
import io.github.smiskinext.meetingmanagement.infrastructure.persistence.model.JoinRequestResultData;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis-backed implementation of {@link JoinRequestResultStore}.
 *
 * <p>Stores terminal outcomes under {@code join_request_result:{requestId}} as a JSON string with
 * a TTL matching the guest SSE timeout. Allows {@code MeetingSseManager.subscribeGuest} to replay
 * the resolution event when the SSE client connects after the host has already acted.
 */
@Repository
public class JoinRequestResultRedisRepositoryAdapter implements JoinRequestResultStore {

    private static final String KEY_PREFIX = "join_request_result:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public JoinRequestResultRedisRepositoryAdapter(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            SseProperties sseProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofMillis(sseProperties.getJoinRequestTimeoutMs());
    }

    @Override
    public void save(JoinRequestResult result) {
        String key = key(result.requestId());
        String payload = objectMapper.writeValueAsString(JoinRequestResultData.from(result));
        stringRedisTemplate.opsForValue().set(key, payload, ttl);
    }

    @Override
    public Optional<JoinRequestResult> findByRequestId(UUID requestId) {
        String payload = stringRedisTemplate.opsForValue().get(key(requestId));
        if (payload == null) {
            return Optional.empty();
        }
        JoinRequestResultData data = objectMapper.readValue(payload, JoinRequestResultData.class);
        return Optional.of(data.toDomain());
    }

    @Override
    public void delete(UUID requestId) {
        stringRedisTemplate.delete(key(requestId));
    }

    private static String key(UUID requestId) {
        return KEY_PREFIX + requestId;
    }
}
