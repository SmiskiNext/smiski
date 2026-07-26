package io.github.smiskinext.meet.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import io.github.smiskinext.meet.domain.model.JoinRequestResult;
import io.github.smiskinext.meet.domain.model.JoinRequestStatus;
import java.time.Duration;
import java.util.Optional;
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
class JoinRequestResultStoreRepositoryAdapterIntegrationTest {

    @Autowired
    private JoinRequestResultStoreRepositoryAdapter store;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        stringRedisTemplate
                .getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushAll();
    }

    @Test
    void savesAndReadsApprovedOutcomeRetainingToken() {
        UUID requestId = UUID.randomUUID();
        store.save(JoinRequestResult.approved(requestId, "the-token", "meeting-" + requestId));

        Optional<JoinRequestResult> found = store.findByRequestId(requestId);

        assertThat(found).isPresent();
        JoinRequestResult result = found.get();
        assertThat(result.status()).isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(result.liveKitToken()).isEqualTo("the-token");
        assertThat(result.roomName()).isEqualTo("meeting-" + requestId);
        assertThat(result.denyReason()).isNull();
        assertThat(stringRedisTemplate.getExpire("join_request_result:" + requestId))
                .isPositive();
    }

    @Test
    void savesAndReadsDeniedOutcomeWithoutToken() {
        UUID requestId = UUID.randomUUID();
        store.save(JoinRequestResult.denied(requestId, null));

        Optional<JoinRequestResult> found = store.findByRequestId(requestId);

        assertThat(found).isPresent();
        JoinRequestResult result = found.get();
        assertThat(result.status()).isEqualTo(JoinRequestStatus.DENIED);
        assertThat(result.liveKitToken()).isNull();
        assertThat(result.roomName()).isNull();
    }

    @Test
    void missingResultReturnsEmpty() {
        assertThat(store.findByRequestId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void deleteRemovesStoredOutcome() {
        UUID requestId = UUID.randomUUID();
        store.save(JoinRequestResult.approved(requestId, "token", "room"));

        store.delete(requestId);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> assertThat(store.findByRequestId(requestId)).isEmpty());
    }
}
