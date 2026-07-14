package io.github.smiskinext.tenant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxRelay;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import io.github.smiskinext.tenant.config.TestcontainersConfiguration;
import io.github.smiskinext.tenant.domain.event.TenantInstalledEvent;
import io.github.smiskinext.tenant.domain.model.TenantStatus;
import io.github.smiskinext.tenant.infrastructure.messaging.OutboxEventPublisher;
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

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OutboxRelaySchedulerIntegrationTest {

    @Autowired
    private OutboxEventPublisher outboxEventPublisher;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant("cloud-relay");
        outboxEventJpaRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void relay_without_tenant_context_selects_and_publishes_rows_across_tenants() {
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

        outboxEventPublisher.publish(event);
        TenantContext.clear();

        assertThat(outboxEventJpaRepository.findUnpublishedOrderByCreatedAt()).hasSize(1);

        outboxRelay.relay();

        List<OutboxEventJpaEntity> afterRelay =
                outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
        assertThat(afterRelay).isEmpty();
    }

    @Test
    void already_published_rows_are_not_resent() {
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

        outboxEventPublisher.publish(event);
        TenantContext.clear();

        outboxRelay.relay();

        List<OutboxEventJpaEntity> unpublished =
                outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
        assertThat(unpublished).isEmpty();

        outboxRelay.relay();
        assertThat(outboxEventJpaRepository.findUnpublishedOrderByCreatedAt()).isEmpty();
    }

    @Test
    void aggregate_id_is_used_as_transport_message_key() {
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

        outboxEventPublisher.publish(event);
        TenantContext.clear();

        List<OutboxEventJpaEntity> rows =
                outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getAggregateId()).isEqualTo("cloud-key-test");

        outboxRelay.relay();

        assertThat(outboxEventJpaRepository.findUnpublishedOrderByCreatedAt()).isEmpty();
    }
}
