package io.github.smiskinext.meet.infrastructure.persistence;

import io.github.smiskinext.meet.domain.model.JoinRequestResult;
import io.github.smiskinext.meet.domain.port.JoinRequestResultStore;
import io.github.smiskinext.meet.infrastructure.persistence.model.JoinRequestResultData;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Redis-backed {@link JoinRequestResultStore}.
 *
 * <p>Persists the terminal outcome of a decided join request under {@code
 * join_request_result:{requestId}} as a JSON {@link JoinRequestResultData} document, serialized
 * through the same restricted Jackson 3 {@code JsonMapper} the join-request queue uses. The entry
 * TTL is aligned to the requester SSE stream timeout (5 minutes) plus a small buffer so a
 * late-subscribing requester can still replay its outcome for the full window.
 */
@Repository
public class JoinRequestResultStoreRepositoryAdapter implements JoinRequestResultStore {

    private static final Duration RESULT_TTL = Duration.ofMinutes(5).plusSeconds(120);

    private final RedisTemplate<String, JoinRequestResultData> joinRequestResultRedisTemplate;

    public JoinRequestResultStoreRepositoryAdapter(
            RedisTemplate<String, JoinRequestResultData> joinRequestResultRedisTemplate) {
        this.joinRequestResultRedisTemplate = joinRequestResultRedisTemplate;
    }

    @Override
    public void save(JoinRequestResult result) {
        String key = resultKey(result.requestId().toString());
        joinRequestResultRedisTemplate
                .opsForValue()
                .set(key, JoinRequestResultData.from(result), RESULT_TTL);
    }

    @Override
    public Optional<JoinRequestResult> findByRequestId(UUID requestId) {
        String key = resultKey(requestId.toString());
        JoinRequestResultData data =
                joinRequestResultRedisTemplate.opsForValue().get(key);
        return Optional.ofNullable(data).map(JoinRequestResultData::toDomain);
    }

    @Override
    public void delete(UUID requestId) {
        joinRequestResultRedisTemplate.delete(resultKey(requestId.toString()));
    }

    private String resultKey(String requestId) {
        return "join_request_result:" + requestId;
    }
}
