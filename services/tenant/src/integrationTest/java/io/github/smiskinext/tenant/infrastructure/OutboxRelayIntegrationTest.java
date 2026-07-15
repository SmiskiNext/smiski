package io.github.smiskinext.tenant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.shared.infrastructure.outbox.EventPublisher;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxRelay;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import io.github.smiskinext.tenant.config.TestcontainersConfiguration;
import io.github.smiskinext.tenant.domain.event.TenantInstalledEvent;
import io.github.smiskinext.tenant.domain.model.TenantStatus;
import io.github.smiskinext.tenant.infrastructure.persistence.OutboxEventJpaEntity;
import io.github.smiskinext.tenant.infrastructure.persistence.OutboxEventJpaRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OutboxRelayIntegrationTest {

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
        TenantContext.setCurrentTenant("cloud-relay");
        transactionTemplate.execute(status -> {
            outboxEventJpaRepository.deleteAll();
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.setCurrentTenant("cloud-relay");
        transactionTemplate.execute(status -> {
            outboxEventJpaRepository.deleteAll();
            return null;
        });
        TenantContext.clear();
    }

    @Test
    void relay_without_tenant_context_selects_and_publishes_rows_across_tenants() {
        transactionTemplate.execute(status -> {
            TenantInstalledEvent event = new TenantInstalledEvent(
                    UuidCreator.getTimeOrderedEpoch(),
                    "cloud-relay",
                    "install-1",
                    "app-1",
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

        TenantContext.setCurrentTenant("cloud-relay");
        List<OutboxEventJpaEntity> afterRelay = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
        });
        assertThat(afterRelay).isEmpty();
    }

    @Test
    void already_published_rows_are_not_resent() {
        transactionTemplate.execute(status -> {
            TenantInstalledEvent event = new TenantInstalledEvent(
                    UuidCreator.getTimeOrderedEpoch(),
                    "cloud-relay",
                    "install-1",
                    "app-1",
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

        TenantContext.clear();
        outboxRelay.relay();

        TenantContext.setCurrentTenant("cloud-relay");
        List<OutboxEventJpaEntity> unpublished = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
        });
        assertThat(unpublished).isEmpty();

        TenantContext.clear();
        outboxRelay.relay();

        TenantContext.setCurrentTenant("cloud-relay");
        List<OutboxEventJpaEntity> afterSecondRelay = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
        });
        assertThat(afterSecondRelay).isEmpty();
    }

    @Test
    void aggregate_id_is_used_as_transport_message_key() {
        TenantContext.setCurrentTenant("cloud-key-test");

        transactionTemplate.execute(status -> {
            TenantInstalledEvent event = new TenantInstalledEvent(
                    UuidCreator.getTimeOrderedEpoch(),
                    "cloud-key-test",
                    "install-1",
                    "app-1",
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

        List<OutboxEventJpaEntity> rows = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
        });
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getAggregateId()).isEqualTo("cloud-key-test");

        TenantContext.clear();
        outboxRelay.relay();

        TenantContext.setCurrentTenant("cloud-key-test");
        List<OutboxEventJpaEntity> afterRelay = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
        });
        assertThat(afterRelay).isEmpty();
    }
}
