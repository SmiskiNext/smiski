package io.github.smiskinext.tenant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.shared.domain.PublishableEvent;
import io.github.smiskinext.shared.infrastructure.outbox.EventPublisher;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import io.github.smiskinext.tenant.config.TestcontainersConfiguration;
import io.github.smiskinext.tenant.domain.model.Tenant;
import io.github.smiskinext.tenant.domain.model.TenantStatus;
import io.github.smiskinext.tenant.domain.model.valueobject.AppId;
import io.github.smiskinext.tenant.domain.model.valueobject.InstallationId;
import io.github.smiskinext.tenant.infrastructure.persistence.OutboxEventJpaEntity;
import io.github.smiskinext.tenant.infrastructure.persistence.OutboxEventJpaRepository;
import io.github.smiskinext.tenant.infrastructure.persistence.TenantJpaRepository;
import io.github.smiskinext.tenant.infrastructure.persistence.TenantRepositoryAdapter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TenantRepositoryAdapterIntegrationTest {

    @Autowired
    private TenantRepositoryAdapter tenantRepository;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private TenantJpaRepository tenantJpaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant("cloud-test");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @Transactional
    void upsert_and_outbox_written_atomically() {
        Tenant tenant = Tenant.install(
                "cloud-test",
                new InstallationId("install-1"),
                new AppId("app-1"),
                "1.0.0",
                null,
                null,
                null);

        tenantRepository.save(tenant);

        tenant.getDomainEvents().stream()
                .filter(PublishableEvent.class::isInstance)
                .map(PublishableEvent.class::cast)
                .forEach(eventPublisher::publish);

        Optional<Tenant> loaded = tenantRepository.findById("cloud-test");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getStatus()).isEqualTo(TenantStatus.ACTIVE);

        List<OutboxEventJpaEntity> outboxRows =
                outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
        List<OutboxEventJpaEntity> relevantRows = outboxRows.stream()
                .filter(row -> row.getAggregateId().equals("cloud-test"))
                .toList();
        assertThat(relevantRows).hasSize(1);
        assertThat(relevantRows.getFirst().getAggregateId()).isEqualTo("cloud-test");
        assertThat(relevantRows.getFirst().getPublishedAt()).isNull();
    }

    @Test
    void rolled_back_upsert_leaves_no_event() {
        String cloudId = "cloud-rollback-test";
        TenantContext.setCurrentTenant(cloudId);

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        try {
            txTemplate.execute(status -> {
                Tenant tenant = Tenant.install(
                        cloudId,
                        new InstallationId("install-rb"),
                        new AppId("app-rb"),
                        "1.0.0",
                        null,
                        null,
                        null);

                tenantRepository.save(tenant);

                tenant.getDomainEvents().stream()
                        .filter(PublishableEvent.class::isInstance)
                        .map(PublishableEvent.class::cast)
                        .forEach(eventPublisher::publish);

                throw new RuntimeException("Simulated failure to trigger rollback");
            });
        } catch (RuntimeException ignored) {
        }

        TransactionTemplate verifyTx = new TransactionTemplate(transactionManager);
        verifyTx.setReadOnly(true);
        verifyTx.execute(status -> {
            assertThat(tenantJpaRepository.findById(cloudId)).isEmpty();

            List<OutboxEventJpaEntity> outboxRows =
                    outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
            assertThat(outboxRows.stream()
                            .filter(row -> row.getAggregateId().equals(cloudId))
                            .toList())
                    .isEmpty();
            return null;
        });
    }
}
