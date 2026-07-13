package io.github.smiskinext.tenant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.cloudevents.CloudEvent;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import io.github.smiskinext.tenant.config.TestcontainersConfiguration;
import io.github.smiskinext.tenant.domain.event.TenantInstalledEvent;
import io.github.smiskinext.tenant.infrastructure.messaging.OutboxEventPublisher;
import io.github.smiskinext.tenant.infrastructure.messaging.OutboxRelayScheduler;
import io.github.smiskinext.tenant.infrastructure.persistence.OutboxEventJpaEntity;
import io.github.smiskinext.tenant.infrastructure.persistence.OutboxEventJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OutboxRelayFailureIntegrationTest {

    @MockitoBean
    private KafkaTemplate<String, CloudEvent> kafkaTemplate;

    @Autowired
    private OutboxEventPublisher outboxEventPublisher;

    @Autowired
    private OutboxRelayScheduler outboxRelayScheduler;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant("cloud-fail");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @Transactional
    void publish_failure_increments_retry_count_and_records_error() {
        CompletableFuture<?> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka broker unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), any(CloudEvent.class)))
                .thenReturn((CompletableFuture) failedFuture);

        TenantInstalledEvent event = new TenantInstalledEvent(
                UuidCreator.getTimeOrderedEpoch(),
                "cloud-fail",
                "install-fail",
                "app-fail",
                "1.0.0",
                null,
                "PRODUCTION",
                null,
                null,
                Instant.now());

        outboxEventPublisher.publish(event);

        assertThat(outboxEventJpaRepository.findUnpublishedOrderByCreatedAt()).hasSize(1);

        outboxRelayScheduler.relay();

        List<OutboxEventJpaEntity> afterRelay =
                outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
        assertThat(afterRelay).hasSize(1);

        OutboxEventJpaEntity row = afterRelay.getFirst();
        assertThat(row.getRetryCount()).isEqualTo(1);
        assertThat(row.getLastError()).isNotNull();
        assertThat(row.getPublishedAt()).isNull();
    }
}
