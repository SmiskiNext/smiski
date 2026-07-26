package io.github.smiskinext.notification.infrastructure.persistence;

import io.github.smiskinext.notification.domain.model.JoinDecision;
import io.github.smiskinext.notification.domain.port.JoinDecisionStore;
import io.github.smiskinext.notification.infrastructure.persistence.model.JoinDecisionData;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Redis-backed {@link JoinDecisionStore}.
 *
 * <p>Persists a host decision under {@code join_decision_meta:{requestId}} as a JSON
 * {@link JoinDecisionData} document so a requester subscribing after the decision arrives — on any
 * replica — can replay its outcome. The entry TTL is aligned to the requester notification window
 * (5 minutes) plus a small buffer.
 */
@Repository
public class JoinDecisionStoreRepositoryAdapter implements JoinDecisionStore {

    private static final Duration ENTRY_TTL = Duration.ofMinutes(5).plusSeconds(120);

    private final RedisTemplate<String, JoinDecisionData> joinDecisionRedisTemplate;

    public JoinDecisionStoreRepositoryAdapter(
            RedisTemplate<String, JoinDecisionData> joinDecisionRedisTemplate) {
        this.joinDecisionRedisTemplate = joinDecisionRedisTemplate;
    }

    @Override
    public void upsert(JoinDecision decision) {
        String key = metaKey(decision.joinRequestId().toString());
        joinDecisionRedisTemplate
                .opsForValue()
                .set(key, JoinDecisionData.from(decision), ENTRY_TTL);
    }

    @Override
    public Optional<JoinDecision> findByRequestId(UUID joinRequestId) {
        String key = metaKey(joinRequestId.toString());
        JoinDecisionData data = joinDecisionRedisTemplate.opsForValue().get(key);
        return Optional.ofNullable(data).map(JoinDecisionData::toDomain);
    }

    private String metaKey(String requestId) {
        return "join_decision_meta:" + requestId;
    }
}
