package io.github.smiskinext.meet.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import io.github.smiskinext.meet.domain.model.JoinRequest;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class JoinRequestRedisRepositoryAdapterIntegrationTest {

    @Autowired
    private JoinRequestRedisRepositoryAdapter repository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private UUID meetingId;

    @BeforeEach
    void setUp() {
        meetingId = UUID.randomUUID();
        stringRedisTemplate
                .getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushAll();
    }

    @Test
    void pendingQueueReflectsCreatedRequestsOrderedByRequestedTime() {
        JoinRequest first = create("account-1", "Alice", "device-1");
        JoinRequest second = create("account-2", "Bob", "device-2");
        repository.save(first, Duration.ofMinutes(5));
        repository.save(second, Duration.ofMinutes(5));

        List<JoinRequest> pending = repository.findPendingByMeetingId(meetingId);

        assertThat(pending).hasSize(2);
        assertThat(pending.get(0).getRequestedAt())
                .isBeforeOrEqualTo(pending.get(1).getRequestedAt());
        assertThat(pending)
                .extracting(request -> request.getDeviceId())
                .containsExactlyInAnyOrder("device-1", "device-2");
    }

    @Test
    void atomicRemovalClearsQueueMetaAndDeviceIndex() {
        JoinRequest request = create("account-1", "Alice", "device-1");
        repository.save(request, Duration.ofMinutes(5));

        repository.removeFromQueue(meetingId, request.getId().value());

        assertThat(repository.findById(request.getId().value())).isEmpty();
        assertThat(repository.findByDeviceId(meetingId, "device-1")).isEmpty();
        assertThat(repository.findPendingByMeetingId(meetingId)).isEmpty();
        assertThat(stringRedisTemplate.opsForZSet().size("join_request:" + meetingId))
                .isZero();
    }

    @Test
    void ttlExpiryRemovesRequestFromQueue() {
        JoinRequest request = create("account-1", "Alice", "device-1");
        repository.save(request, Duration.ofSeconds(1));

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        assertThat(repository.findById(request.getId().value())).isEmpty());
    }

    private JoinRequest create(String accountId, String displayName, String deviceId) {
        return JoinRequest.create(
                MeetingId.of(meetingId),
                AccountId.of(accountId),
                displayName,
                deviceId,
                "https://cdn.example.com/avatar/" + accountId + ".png",
                Instant.now().plus(Duration.ofMinutes(5)));
    }
}
