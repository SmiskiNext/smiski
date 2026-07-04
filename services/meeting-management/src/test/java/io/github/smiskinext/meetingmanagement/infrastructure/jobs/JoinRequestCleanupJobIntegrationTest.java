package io.github.smiskinext.meetingmanagement.infrastructure.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import io.github.smiskinext.meetingmanagement.config.RedisTestcontainersConfiguration;
import io.github.smiskinext.meetingmanagement.config.TestcontainersConfiguration;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequest;
import io.github.smiskinext.meetingmanagement.infrastructure.persistence.JoinRequestRedisRepositoryAdapter;
import io.github.phunguy65.zms.shared.domain.valueobject.MeetingId;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration test for {@link JoinRequestCleanupJob} verifying SCAN-based iteration
 * and empty ZSET cleanup against a real Redis instance.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class JoinRequestCleanupJobTest {

    @Autowired
    JoinRequestCleanupJob cleanupJob;

    @Autowired
    JoinRequestRedisRepositoryAdapter adapter;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    ApplicationEventPublisher applicationEventPublisher;

    @BeforeEach
    void clearRedis() {
        stringRedisTemplate
                .getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushDb();
        doNothing().when(applicationEventPublisher).publishEvent(any());
    }

    @Test
    void cleanupExpiredRequests_removesExpiredAndDeletesEmptyZset() {
        UUID meetingId = UUID.randomUUID();
        JoinRequest expired = JoinRequest.create(
                MeetingId.of(meetingId),
                UserId.of(UUID.randomUUID()),
                "ExpiredUser",
                UUID.randomUUID().toString(),
                Instant.now().minusSeconds(60));
        adapter.save(expired, Duration.ofMinutes(5));

        String queueKey = "join_request:" + meetingId;
        assertThat(stringRedisTemplate.hasKey(queueKey)).isTrue();

        cleanupJob.cleanupExpiredRequests();

        Set<String> remaining = stringRedisTemplate.opsForZSet().range(queueKey, 0, -1);
        assertThat(remaining == null || remaining.isEmpty()).isTrue();
        assertThat(stringRedisTemplate.hasKey(queueKey)).isFalse();
    }

    @Test
    void cleanupExpiredRequests_leavesNonExpiredRequests() {
        UUID meetingId = UUID.randomUUID();
        JoinRequest active = JoinRequest.create(
                MeetingId.of(meetingId),
                UserId.of(UUID.randomUUID()),
                "ActiveUser",
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(300));
        adapter.save(active, Duration.ofMinutes(5));

        cleanupJob.cleanupExpiredRequests();

        String queueKey = "join_request:" + meetingId;
        assertThat(stringRedisTemplate.hasKey(queueKey)).isTrue();
        Long size = stringRedisTemplate.opsForZSet().zCard(queueKey);
        assertThat(size).isEqualTo(1L);
    }

    @Test
    void cleanupExpiredRequests_noOrphanZsetsAfterMixedCleanup() {
        UUID meetingId = UUID.randomUUID();
        JoinRequest expired1 = JoinRequest.create(
                MeetingId.of(meetingId),
                UserId.of(UUID.randomUUID()),
                "Expired1",
                UUID.randomUUID().toString(),
                Instant.now().minusSeconds(120));
        JoinRequest expired2 = JoinRequest.create(
                MeetingId.of(meetingId),
                UserId.of(UUID.randomUUID()),
                "Expired2",
                UUID.randomUUID().toString(),
                Instant.now().minusSeconds(60));
        adapter.save(expired1, Duration.ofMinutes(5));
        adapter.save(expired2, Duration.ofMinutes(5));

        cleanupJob.cleanupExpiredRequests();

        String queueKey = "join_request:" + meetingId;
        assertThat(stringRedisTemplate.hasKey(queueKey)).isFalse();
    }

    @Test
    void cleanupExpiredRequests_handlesMultipleMeetingsViaScan() {
        UUID meetingId1 = UUID.randomUUID();
        UUID meetingId2 = UUID.randomUUID();
        UUID meetingId3 = UUID.randomUUID();

        JoinRequest expired1 = JoinRequest.create(
                MeetingId.of(meetingId1),
                UserId.of(UUID.randomUUID()),
                "User1",
                UUID.randomUUID().toString(),
                Instant.now().minusSeconds(60));
        JoinRequest expired2 = JoinRequest.create(
                MeetingId.of(meetingId2),
                UserId.of(UUID.randomUUID()),
                "User2",
                UUID.randomUUID().toString(),
                Instant.now().minusSeconds(60));
        JoinRequest active = JoinRequest.create(
                MeetingId.of(meetingId3),
                UserId.of(UUID.randomUUID()),
                "ActiveUser",
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(300));
        adapter.save(expired1, Duration.ofMinutes(5));
        adapter.save(expired2, Duration.ofMinutes(5));
        adapter.save(active, Duration.ofMinutes(5));

        cleanupJob.cleanupExpiredRequests();

        assertThat(stringRedisTemplate.hasKey("join_request:" + meetingId1)).isFalse();
        assertThat(stringRedisTemplate.hasKey("join_request:" + meetingId2)).isFalse();
        assertThat(stringRedisTemplate.hasKey("join_request:" + meetingId3)).isTrue();
        Long activeSize = stringRedisTemplate.opsForZSet().zCard("join_request:" + meetingId3);
        assertThat(activeSize).isEqualTo(1L);
    }

    @Test
    void cleanupExpiredRequests_partialExpiryLeavesZsetWithRemainingMembers() {
        UUID meetingId = UUID.randomUUID();
        JoinRequest expired = JoinRequest.create(
                MeetingId.of(meetingId),
                UserId.of(UUID.randomUUID()),
                "ExpiredUser",
                UUID.randomUUID().toString(),
                Instant.now().minusSeconds(60));
        JoinRequest active = JoinRequest.create(
                MeetingId.of(meetingId),
                UserId.of(UUID.randomUUID()),
                "ActiveUser",
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(300));
        adapter.save(expired, Duration.ofMinutes(5));
        adapter.save(active, Duration.ofMinutes(5));

        cleanupJob.cleanupExpiredRequests();

        String queueKey = "join_request:" + meetingId;
        assertThat(stringRedisTemplate.hasKey(queueKey)).isTrue();
        Long size = stringRedisTemplate.opsForZSet().zCard(queueKey);
        assertThat(size).isEqualTo(1L);
    }

    @Test
    void cleanupExpiredRequests_usesScanInsteadOfKeys() {
        UUID meetingId = UUID.randomUUID();
        JoinRequest expired = JoinRequest.create(
                MeetingId.of(meetingId),
                UserId.of(UUID.randomUUID()),
                "ExpiredUser",
                UUID.randomUUID().toString(),
                Instant.now().minusSeconds(60));
        adapter.save(expired, Duration.ofMinutes(5));

        resetCommandStats();

        cleanupJob.cleanupExpiredRequests();

        Properties commandStats = getCommandStats();
        assertThat(commandStats.stringPropertyNames().stream()
                        .anyMatch(key -> key.contains("keys")))
                .as("KEYS command should not be issued during cleanup")
                .isFalse();
        assertThat(commandStats.stringPropertyNames().stream()
                        .anyMatch(key -> key.contains("scan")))
                .as("SCAN command should be used for queue key discovery")
                .isTrue();
    }

    @Test
    void cleanupExpiredRequests_noOrphanZsetsAcrossMultipleMeetingsAfterFullExpiry() {
        UUID meetingId1 = UUID.randomUUID();
        UUID meetingId2 = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            adapter.save(
                    JoinRequest.create(
                            MeetingId.of(meetingId1),
                            UserId.of(UUID.randomUUID()),
                            "User-" + i,
                            UUID.randomUUID().toString(),
                            Instant.now().minusSeconds(60)),
                    Duration.ofMinutes(5));
        }
        for (int i = 0; i < 3; i++) {
            adapter.save(
                    JoinRequest.create(
                            MeetingId.of(meetingId2),
                            UserId.of(UUID.randomUUID()),
                            "User-" + i,
                            UUID.randomUUID().toString(),
                            Instant.now().minusSeconds(60)),
                    Duration.ofMinutes(5));
        }

        cleanupJob.cleanupExpiredRequests();

        assertThat(stringRedisTemplate.hasKey("join_request:" + meetingId1)).isFalse();
        assertThat(stringRedisTemplate.hasKey("join_request:" + meetingId2)).isFalse();
    }

    private void resetCommandStats() {
        stringRedisTemplate
                .getConnectionFactory()
                .getConnection()
                .serverCommands()
                .resetConfigStats();
    }

    private Properties getCommandStats() {
        return stringRedisTemplate
                .getConnectionFactory()
                .getConnection()
                .serverCommands()
                .info("commandstats");
    }
}
