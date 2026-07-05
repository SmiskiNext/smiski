package io.github.smiskinext.meetingmanagement.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meetingmanagement.config.RedisTestcontainersConfiguration;
import io.github.smiskinext.meetingmanagement.config.TestcontainersConfiguration;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequest;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestStatus;
import io.github.smiskinext.meetingmanagement.infrastructure.persistence.model.JoinRequestData;
import io.github.smiskinext.shared.domain.valueobject.MeetingId;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for {@link JoinRequestRedisRepositoryAdapter} verifying atomic Lua script
 * operations, MGET bulk reads, and ZSET TTL propagation against a real Redis instance.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class JoinRequestRedisRepositoryAdapterTest {

    @Autowired
    JoinRequestRedisRepositoryAdapter adapter;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    RedisTemplate<String, JoinRequestData> joinRequestRedisTemplate;

    private static final Duration TEST_TTL = Duration.ofMinutes(5);
    private static final Duration ZSET_TTL_BUFFER = Duration.ofSeconds(120);

    @BeforeEach
    void clearRedis() {
        stringRedisTemplate
                .getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushDb();
    }

    @Test
    void save_setsZsetTtlAndCreatesAllThreeKeysAtomically() {
        UUID meetingId = UUID.randomUUID();
        JoinRequest request = createPendingRequest(meetingId);

        adapter.save(request, TEST_TTL);

        String queueKey = "join_request:" + meetingId;
        String metaKey = "join_request_meta:" + request.getId().value();
        String deviceKey = "join_request_device:" + meetingId + ":" + request.getDeviceId();

        assertThat(stringRedisTemplate.hasKey(queueKey)).isTrue();
        assertThat(joinRequestRedisTemplate.hasKey(metaKey)).isTrue();
        assertThat(stringRedisTemplate.hasKey(deviceKey)).isTrue();

        Long zsetTtl = stringRedisTemplate.getExpire(queueKey, TimeUnit.SECONDS);
        assertThat(zsetTtl).isNotNull();
        long expectedMaxTtl = TEST_TTL.plus(ZSET_TTL_BUFFER).toSeconds();
        assertThat(zsetTtl).isBetween(expectedMaxTtl - 5, expectedMaxTtl);

        Long metaTtl = joinRequestRedisTemplate.getExpire(metaKey, TimeUnit.SECONDS);
        assertThat(metaTtl).isNotNull().isPositive();

        Long deviceTtl = stringRedisTemplate.getExpire(deviceKey, TimeUnit.SECONDS);
        assertThat(deviceTtl).isNotNull().isPositive();
    }

    @Test
    void save_refreshesZsetTtlOnSubsequentSavesForSameMeeting() {
        UUID meetingId = UUID.randomUUID();
        JoinRequest first = createPendingRequest(meetingId);
        JoinRequest second = createPendingRequest(meetingId);

        adapter.save(first, TEST_TTL);
        adapter.save(second, TEST_TTL);

        String queueKey = "join_request:" + meetingId;
        Long zsetTtl = stringRedisTemplate.getExpire(queueKey, TimeUnit.SECONDS);
        long expectedMaxTtl = TEST_TTL.plus(ZSET_TTL_BUFFER).toSeconds();
        assertThat(zsetTtl).isNotNull().isBetween(expectedMaxTtl - 5, expectedMaxTtl);

        Long size = stringRedisTemplate.opsForZSet().zCard(queueKey);
        assertThat(size).isEqualTo(2L);
    }

    @Test
    void updateStatus_atomicallyUpdatesStatusAndPreservesTtl() {
        UUID meetingId = UUID.randomUUID();
        JoinRequest request = createPendingRequest(meetingId);
        adapter.save(request, TEST_TTL);

        adapter.updateStatus(request.getId().value(), JoinRequestStatus.APPROVED);

        var found = adapter.findById(request.getId().value());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(JoinRequestStatus.APPROVED);

        String metaKey = "join_request_meta:" + request.getId().value();
        Long ttl = joinRequestRedisTemplate.getExpire(metaKey, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isPositive();
    }

    @Test
    void updateStatus_noOpForNonExistentKey() {
        UUID nonExistentId = UUID.randomUUID();

        adapter.updateStatus(nonExistentId, JoinRequestStatus.EXPIRED);

        var found = adapter.findById(nonExistentId);
        assertThat(found).isEmpty();
    }

    @Test
    void findPendingByMeetingId_usesMgetAndFiltersPendingOnly() {
        UUID meetingId = UUID.randomUUID();

        JoinRequest pending1 = createPendingRequest(meetingId);
        JoinRequest pending2 = createPendingRequest(meetingId);
        JoinRequest pending3 = createPendingRequest(meetingId);
        adapter.save(pending1, TEST_TTL);
        adapter.save(pending2, TEST_TTL);
        adapter.save(pending3, TEST_TTL);

        adapter.updateStatus(pending2.getId().value(), JoinRequestStatus.APPROVED);

        List<JoinRequest> results = adapter.findPendingByMeetingId(meetingId);
        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(r -> r.getId().value())
                .containsExactlyInAnyOrder(
                        pending1.getId().value(), pending3.getId().value());
    }

    @Test
    void findPendingByMeetingId_with100Entries_returnsAllPending() {
        UUID meetingId = UUID.randomUUID();
        for (int i = 0; i < 100; i++) {
            JoinRequest request = createPendingRequest(meetingId);
            adapter.save(request, TEST_TTL);
        }

        List<JoinRequest> results = adapter.findPendingByMeetingId(meetingId);
        assertThat(results).hasSize(100);
    }

    @Test
    void removeFromQueue_atomicallyRemovesAllAssociatedKeys() {
        UUID meetingId = UUID.randomUUID();
        JoinRequest request = createPendingRequest(meetingId);
        adapter.save(request, TEST_TTL);

        adapter.removeFromQueue(meetingId, request.getId().value());

        String queueKey = "join_request:" + meetingId;
        String metaKey = "join_request_meta:" + request.getId().value();
        String deviceKey = "join_request_device:" + meetingId + ":" + request.getDeviceId();

        Long zsetSize = stringRedisTemplate.opsForZSet().zCard(queueKey);
        assertThat(zsetSize).isEqualTo(0L);
        assertThat(joinRequestRedisTemplate.hasKey(metaKey)).isFalse();
        assertThat(stringRedisTemplate.hasKey(deviceKey)).isFalse();
    }

    @Test
    void deleteAllByMeetingId_removesZsetAndAllRelatedKeys() {
        UUID meetingId = UUID.randomUUID();
        JoinRequest r1 = createPendingRequest(meetingId);
        JoinRequest r2 = createPendingRequest(meetingId);
        adapter.save(r1, TEST_TTL);
        adapter.save(r2, TEST_TTL);

        adapter.deleteAllByMeetingId(meetingId);

        String queueKey = "join_request:" + meetingId;
        assertThat(stringRedisTemplate.hasKey(queueKey)).isFalse();
        assertThat(joinRequestRedisTemplate.hasKey(
                        "join_request_meta:" + r1.getId().value()))
                .isFalse();
        assertThat(joinRequestRedisTemplate.hasKey(
                        "join_request_meta:" + r2.getId().value()))
                .isFalse();
        assertThat(stringRedisTemplate.hasKey(
                        "join_request_device:" + meetingId + ":" + r1.getDeviceId()))
                .isFalse();
        assertThat(stringRedisTemplate.hasKey(
                        "join_request_device:" + meetingId + ":" + r2.getDeviceId()))
                .isFalse();
    }

    @Test
    void updateStatus_atomicUnderConcurrentModification() throws Exception {
        UUID meetingId = UUID.randomUUID();
        JoinRequest request = createPendingRequest(meetingId);
        adapter.save(request, TEST_TTL);

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            JoinRequestStatus status =
                    (i % 2 == 0) ? JoinRequestStatus.APPROVED : JoinRequestStatus.DENIED;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    adapter.updateStatus(request.getId().value(), status);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        var found = adapter.findById(request.getId().value());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus())
                .isIn(JoinRequestStatus.APPROVED, JoinRequestStatus.DENIED);

        String metaKey = "join_request_meta:" + request.getId().value();
        Long ttl = joinRequestRedisTemplate.getExpire(metaKey, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isPositive();
    }

    @Test
    void updateStatus_preservesTtlNearExpiry() throws Exception {
        UUID meetingId = UUID.randomUUID();
        JoinRequest request = createPendingRequest(meetingId);
        adapter.save(request, Duration.ofSeconds(3));

        Thread.sleep(2000);

        adapter.updateStatus(request.getId().value(), JoinRequestStatus.APPROVED);

        String metaKey = "join_request_meta:" + request.getId().value();
        Long ttl = joinRequestRedisTemplate.getExpire(metaKey, TimeUnit.MILLISECONDS);
        assertThat(ttl).isNotNull().isPositive();

        var found = adapter.findById(request.getId().value());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(JoinRequestStatus.APPROVED);
    }

    @Test
    void findPendingByMeetingId_filtersNullsFromMgetWhenMetaKeysExpireBetweenZrangeAndMget() {
        UUID meetingId = UUID.randomUUID();
        JoinRequest alive = createPendingRequest(meetingId);
        JoinRequest expiring = createPendingRequest(meetingId);

        adapter.save(alive, TEST_TTL);
        adapter.save(expiring, TEST_TTL);

        String expiringMetaKey = "join_request_meta:" + expiring.getId().value();
        joinRequestRedisTemplate.delete(expiringMetaKey);

        List<JoinRequest> results = adapter.findPendingByMeetingId(meetingId);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getId().value()).isEqualTo(alive.getId().value());
    }

    @Test
    void findPendingSummariesByMeetingId_returnsPaginatedPendingRequestsViaMget() {
        UUID meetingId = UUID.randomUUID();
        for (int i = 0; i < 25; i++) {
            JoinRequest request = createPendingRequest(meetingId);
            adapter.save(request, TEST_TTL);
        }

        var page1 = adapter.findPendingSummariesByMeetingId(meetingId, 0, 10);
        assertThat(page1.items()).hasSize(10);
        assertThat(page1.hasNext()).isTrue();

        var page2 = adapter.findPendingSummariesByMeetingId(meetingId, 10, 10);
        assertThat(page2.items()).hasSize(10);
        assertThat(page2.hasNext()).isTrue();

        var page3 = adapter.findPendingSummariesByMeetingId(meetingId, 20, 10);
        assertThat(page3.items()).hasSize(5);
        assertThat(page3.hasNext()).isFalse();
    }

    private JoinRequest createPendingRequest(UUID meetingId) {
        return JoinRequest.create(
                MeetingId.of(meetingId),
                UserId.of(UUID.randomUUID()),
                "User-" + UUID.randomUUID().toString().substring(0, 6),
                UUID.randomUUID().toString(),
                Instant.now().plus(Duration.ofMinutes(5)));
    }
}
