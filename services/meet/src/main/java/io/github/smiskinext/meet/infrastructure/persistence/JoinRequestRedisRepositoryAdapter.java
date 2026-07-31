package io.github.smiskinext.meet.infrastructure.persistence;

import io.github.smiskinext.meet.domain.model.JoinRequest;
import io.github.smiskinext.meet.domain.model.JoinRequestStatus;
import io.github.smiskinext.meet.domain.port.JoinRequestRepository;
import io.github.smiskinext.meet.domain.projection.JoinRequestSummary;
import io.github.smiskinext.meet.infrastructure.config.RedisConfig;
import io.github.smiskinext.meet.infrastructure.persistence.model.JoinRequestData;
import io.github.smiskinext.shared.domain.OffsetPageResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis-backed repository for join requests.
 *
 * <p>Data structures:
 *
 * <ul>
 *   <li>{@code ZSET join_request:{meetingId}} — pending queue ordered by {@code expiresAt} (score)
 *   <li>{@code STRING join_request_meta:{requestId}} — request metadata (JSON)
 *   <li>{@code STRING join_request_device:{meetingId}:{deviceId}} — duplicate-detection index
 * </ul>
 *
 * <p>All multi-key mutations ({@code save}, {@code removeFromQueue}, {@code deleteAllByMeetingId},
 * {@code updateStatus}) run as atomic Lua scripts so the queue, metadata, and device index never
 * diverge. Request-scoped keys receive the caller's TTL; the ZSET receives that TTL plus a buffer so
 * a still-referenced queue is not evicted before its members.
 */
@Repository
public class JoinRequestRedisRepositoryAdapter implements JoinRequestRepository {

    private static final Duration ZSET_TTL_BUFFER = Duration.ofSeconds(120);

    private static final String SAVE_SCRIPT = """
            local queueKey  = KEYS[1]
            local metaKey   = KEYS[2]
            local deviceKey = KEYS[3]
            local requestId = ARGV[1]
            local score     = tonumber(ARGV[2])
            local metaJson  = ARGV[3]
            local ttlMs     = tonumber(ARGV[4])
            local zsetTtlMs = tonumber(ARGV[5])
            redis.call('ZADD', queueKey, score, requestId)
            redis.call('PEXPIRE', queueKey, zsetTtlMs)
            redis.call('SET', metaKey, metaJson, 'PX', ttlMs)
            redis.call('SET', deviceKey, requestId, 'PX', ttlMs)
            return 1
            """;

    private static final String REMOVE_FROM_QUEUE_SCRIPT = """
            local queueKey  = KEYS[1]
            local metaKey   = KEYS[2]
            local metaJson  = redis.call('GET', metaKey)
            if metaJson then
                local data = cjson.decode(metaJson)
                local deviceId = data['deviceId']
                if deviceId then
                    local deviceKey = ARGV[1] .. deviceId
                    redis.call('DEL', deviceKey)
                end
            end
            redis.call('ZREM', queueKey, ARGV[2])
            redis.call('DEL', metaKey)
            return 1
            """;

    private static final String DELETE_ALL_BY_MEETING_SCRIPT = """
            local queueKey     = KEYS[1]
            local metaPrefix   = ARGV[1]
            local devicePrefix = ARGV[2]
            local members = redis.call('ZRANGE', queueKey, 0, -1)
            for _, requestId in ipairs(members) do
                local metaKey = metaPrefix .. requestId
                local metaJson = redis.call('GET', metaKey)
                if metaJson then
                    local data = cjson.decode(metaJson)
                    local deviceId = data['deviceId']
                    if deviceId then
                        redis.call('DEL', devicePrefix .. deviceId)
                    end
                end
                redis.call('DEL', metaKey)
            end
            redis.call('DEL', queueKey)
            return #members
            """;

    private static final String UPDATE_STATUS_SCRIPT = """
            local metaKey  = KEYS[1]
            local newStatus = ARGV[1]
            local metaJson = redis.call('GET', metaKey)
            if not metaJson then
                return 0
            end
            local data = cjson.decode(metaJson)
            data['status'] = newStatus
            local updatedJson = cjson.encode(data)
            local pttl = redis.call('PTTL', metaKey)
            if pttl > 0 then
                redis.call('SET', metaKey, updatedJson, 'PX', pttl)
            else
                redis.call('SET', metaKey, updatedJson)
            end
            return 1
            """;

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, JoinRequestData> joinRequestRedisTemplate;
    private final JsonMapper jsonMapper;

    private final DefaultRedisScript<Long> saveScript;
    private final DefaultRedisScript<Long> removeFromQueueScript;
    private final DefaultRedisScript<Long> deleteAllByMeetingScript;
    private final DefaultRedisScript<Long> updateStatusScript;

    public JoinRequestRedisRepositoryAdapter(
            StringRedisTemplate stringRedisTemplate,
            RedisTemplate<String, JoinRequestData> joinRequestRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.joinRequestRedisTemplate = joinRequestRedisTemplate;
        this.jsonMapper = RedisConfig.joinRequestJsonMapper();

        this.saveScript = new DefaultRedisScript<>(SAVE_SCRIPT, Long.class);
        this.removeFromQueueScript = new DefaultRedisScript<>(REMOVE_FROM_QUEUE_SCRIPT, Long.class);
        this.deleteAllByMeetingScript =
                new DefaultRedisScript<>(DELETE_ALL_BY_MEETING_SCRIPT, Long.class);
        this.updateStatusScript = new DefaultRedisScript<>(UPDATE_STATUS_SCRIPT, Long.class);
    }

    @Override
    public void save(JoinRequest request, Duration ttl) {
        String meetingId = request.getMeetingId().value().toString();
        String requestId = request.getId().value().toString();
        String deviceId = request.getDeviceId();

        String queueKey = queueKey(meetingId);
        String metaKey = metaKey(requestId);
        String deviceKey = deviceKey(meetingId, deviceId);

        JoinRequestData data = JoinRequestData.from(request);
        String metaJson = jsonMapper.writeValueAsString(data);

        long ttlMs = ttl.toMillis();
        long zsetTtlMs = ttl.plus(ZSET_TTL_BUFFER).toMillis();
        double score = request.getExpiresAt().toEpochMilli();

        stringRedisTemplate.execute(
                saveScript,
                List.of(queueKey, metaKey, deviceKey),
                requestId,
                String.valueOf(score),
                metaJson,
                String.valueOf(ttlMs),
                String.valueOf(zsetTtlMs));
    }

    @Override
    public Optional<JoinRequest> findById(UUID requestId) {
        String metaKey = metaKey(requestId.toString());
        JoinRequestData data = joinRequestRedisTemplate.opsForValue().get(metaKey);
        return Optional.ofNullable(data).map(JoinRequestData::toDomain);
    }

    @Override
    public Optional<JoinRequest> findByDeviceId(UUID meetingId, String deviceId) {
        String deviceKey = deviceKey(meetingId.toString(), deviceId);
        String requestId = stringRedisTemplate.opsForValue().get(deviceKey);
        if (requestId == null) {
            return Optional.empty();
        }
        return findById(UUID.fromString(requestId));
    }

    @Override
    public List<JoinRequest> findPendingByMeetingId(UUID meetingId) {
        List<JoinRequestData> dataList = loadQueue(meetingId);
        return dataList.stream()
                .filter(Objects::nonNull)
                .filter(data ->
                        JoinRequestStatus.valueOf(data.status()) == JoinRequestStatus.PENDING)
                .map(JoinRequestData::toDomain)
                .sorted(Comparator.comparing(JoinRequest::getRequestedAt))
                .collect(Collectors.toList());
    }

    @Override
    public OffsetPageResponse<JoinRequestSummary> findPendingSummariesByMeetingId(
            UUID meetingId, int offset, int pageSize) {
        List<JoinRequestData> dataList = loadQueue(meetingId);
        if (dataList.isEmpty()) {
            return OffsetPageResponse.empty(pageSize, offset);
        }

        List<JoinRequestSummary> allItems = dataList.stream()
                .filter(Objects::nonNull)
                .filter(data ->
                        JoinRequestStatus.valueOf(data.status()) == JoinRequestStatus.PENDING)
                .map(this::toSummary)
                .sorted(Comparator.comparing(JoinRequestSummary::requestedAt))
                .collect(Collectors.toList());

        if (offset >= allItems.size()) {
            return OffsetPageResponse.empty(pageSize, offset);
        }

        int endExclusive = Math.min(offset + pageSize, allItems.size());
        boolean hasNext = endExclusive < allItems.size();
        return OffsetPageResponse.of(
                allItems.subList(offset, endExclusive), pageSize, offset, hasNext);
    }

    @Override
    public long countPendingByMeetingId(UUID meetingId) {
        return loadQueue(meetingId).stream()
                .filter(Objects::nonNull)
                .filter(data ->
                        JoinRequestStatus.valueOf(data.status()) == JoinRequestStatus.PENDING)
                .count();
    }

    @Override
    public void updateStatus(UUID requestId, JoinRequestStatus status) {
        String metaKey = metaKey(requestId.toString());
        stringRedisTemplate.execute(updateStatusScript, List.of(metaKey), status.name());
    }

    @Override
    public void removeFromQueue(UUID meetingId, UUID requestId) {
        String queueKey = queueKey(meetingId.toString());
        String metaKey = metaKey(requestId.toString());
        String deviceKeyPrefix = "join_request_device:" + meetingId + ":";

        stringRedisTemplate.execute(
                removeFromQueueScript,
                List.of(queueKey, metaKey),
                deviceKeyPrefix,
                requestId.toString());
    }

    @Override
    public void deleteAllByMeetingId(UUID meetingId) {
        String queueKey = queueKey(meetingId.toString());
        String metaPrefix = "join_request_meta:";
        String devicePrefix = "join_request_device:" + meetingId + ":";

        stringRedisTemplate.execute(
                deleteAllByMeetingScript, List.of(queueKey), metaPrefix, devicePrefix);
    }

    private List<JoinRequestData> loadQueue(UUID meetingId) {
        String queueKey = queueKey(meetingId.toString());
        Set<String> requestIds = stringRedisTemplate.opsForZSet().range(queueKey, 0, -1);
        if (requestIds == null || requestIds.isEmpty()) {
            return List.of();
        }
        List<String> metaKeys = requestIds.stream().map(this::metaKey).collect(Collectors.toList());
        List<JoinRequestData> dataList = joinRequestRedisTemplate.opsForValue().multiGet(metaKeys);
        return dataList == null ? List.of() : dataList;
    }

    private String queueKey(String meetingId) {
        return "join_request:" + meetingId;
    }

    private String metaKey(String requestId) {
        return "join_request_meta:" + requestId;
    }

    private String deviceKey(String meetingId, String deviceId) {
        return "join_request_device:" + meetingId + ":" + deviceId;
    }

    private JoinRequestSummary toSummary(JoinRequestData data) {
        return new JoinRequestSummary(
                UUID.fromString(data.id()),
                UUID.fromString(data.meetingId()),
                data.accountId(),
                data.displayName(),
                JoinRequestStatus.valueOf(data.status()),
                Instant.parse(data.requestedAt()),
                Instant.parse(data.expiresAt()));
    }
}
