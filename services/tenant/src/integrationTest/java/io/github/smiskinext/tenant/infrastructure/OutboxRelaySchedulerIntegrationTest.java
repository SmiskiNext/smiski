package io.github.smiskinext.tenant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import io.github.smiskinext.tenant.config.TestcontainersConfiguration;
import io.github.smiskinext.tenant.domain.event.TenantInstalledEvent;
import io.github.smiskinext.tenant.infrastructure.messaging.OutboxEventPublisher;
import io.github.smiskinext.tenant.infrastructure.messaging.OutboxRelayScheduler;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OutboxRelaySchedulerIntegrationTest {

    @Autowired
    private OutboxEventPublisher outboxEventPublisher;

    @Autowired
    private OutboxRelayScheduler outboxRelayScheduler;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant("cloud-relay");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @Transactional
    void relays_unpublished_rows_and_marks_published() {
        TenantInstalledEvent event = new TenantInstalledEvent(
                UuidCreator.getTimeOrderedEpoch(),
                "cloud-relay",
                "install-1",
                "app-1",
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
        assertThat(afterRelay).isEmpty();
    }

    @Test
    @Transactional
    void already_published_rows_are_not_resent() {
        TenantInstalledEvent event = new TenantInstalledEvent(
                UuidCreator.getTimeOrderedEpoch(),
                "cloud-relay",
                "install-1",
                "app-1",
                "1.0.0",
                null,
                "PRODUCTION",
                null,
                null,
                Instant.now());

        outboxEventPublisher.publish(event);
        outboxRelayScheduler.relay();

        List<OutboxEventJpaEntity> unpublished =
                outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
        assertThat(unpublished).isEmpty();

        outboxRelayScheduler.relay();
        assertThat(outboxEventJpaRepository.findUnpublishedOrderByCreatedAt()).isEmpty();
    }
}
