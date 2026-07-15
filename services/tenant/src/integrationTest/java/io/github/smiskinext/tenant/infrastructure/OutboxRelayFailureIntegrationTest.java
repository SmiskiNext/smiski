package io.github.smiskinext.tenant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.cloudevents.CloudEvent;
import io.github.smiskinext.shared.infrastructure.outbox.EventPublisher;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxRelay;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxTransport;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import io.github.smiskinext.tenant.config.TestcontainersConfiguration;
import io.github.smiskinext.tenant.domain.event.TenantInstalledEvent;
import io.github.smiskinext.tenant.domain.model.TenantStatus;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OutboxRelayFailureIntegrationTest {

    @MockitoBean
    private OutboxTransport outboxTransport;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant("cloud-fail");
        transactionTemplate.execute(status -> {
            outboxEventJpaRepository.deleteAll();
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.setCurrentTenant("cloud-fail");
        transactionTemplate.execute(status -> {
            outboxEventJpaRepository.deleteAll();
            return null;
        });
        TenantContext.clear();
    }

    @Test
    void publish_failure_increments_retry_count_and_records_error() {
        CompletableFuture<Void> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka broker unavailable"));
        when(outboxTransport.send(anyString(), anyString(), any(CloudEvent.class)))
                .thenReturn(failedFuture);

        transactionTemplate.execute(status -> {
            TenantInstalledEvent event = new TenantInstalledEvent(
                    UuidCreator.getTimeOrderedEpoch(),
                    "cloud-fail",
                    "install-fail",
                    "app-fail",
                    "1.0.0",
                    null,
                    null,
                    null,
                    Instant.now(),
                    TenantStatus.ACTIVE,
                    Instant.now(),
                    null,
                    null);

            eventPublisher.publish(event);
            return null;
        });

        List<OutboxEventJpaEntity> beforeRelay = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
        });
        assertThat(beforeRelay).hasSize(1);

        TenantContext.clear();
        outboxRelay.relay();

        TenantContext.setCurrentTenant("cloud-fail");
        List<OutboxEventJpaEntity> afterRelay = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
        });
        assertThat(afterRelay).hasSize(1);

        OutboxEventJpaEntity row = afterRelay.getFirst();
        assertThat(row.getRetryCount()).isEqualTo(1);
        assertThat(row.getLastError()).isNotNull();
        assertThat(row.getPublishedAt()).isNull();
    }
}
