package io.github.smiskinext.tenant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.shared.domain.PublishableEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventPublisher;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import io.github.smiskinext.tenant.config.TestcontainersConfiguration;
import io.github.smiskinext.tenant.domain.event.TenantInstalledEvent;
import io.github.smiskinext.tenant.domain.event.TenantUninstalledEvent;
import io.github.smiskinext.tenant.domain.model.TenantStatus;
import io.github.smiskinext.tenant.infrastructure.persistence.OutboxEventJpaEntity;
import io.github.smiskinext.tenant.infrastructure.persistence.OutboxEventJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OutboxEventPublisherIntegrationTest {

    @Autowired
    private OutboxEventPublisher outboxEventPublisher;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant("cloud-payload");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        TenantContext.setCurrentTenant("cloud-payload");
        transactionTemplate.execute(status -> {
            outboxEventJpaRepository.deleteAll();
            return null;
        });
        TenantContext.clear();
    }

    @Test
    void event_enqueued_atomically_with_tenant_id_and_aggregate_id() {
        transactionTemplate.execute(status -> {
            TenantInstalledEvent event = new TenantInstalledEvent(
                    UuidCreator.getTimeOrderedEpoch(),
                    "cloud-payload",
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
            return null;
        });

        List<OutboxEventJpaEntity> rows = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
        });
        assertThat(rows).hasSize(1);

        OutboxEventJpaEntity row = rows.getFirst();
        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getTenantId()).isEqualTo("cloud-payload");
        assertThat(row.getAggregateId()).isEqualTo("cloud-payload");
    }

    @Test
    void payload_is_cloudevent_with_proto_json_data() throws Exception {
        transactionTemplate.execute(status -> {
            TenantInstalledEvent event = new TenantInstalledEvent(
                    UuidCreator.getTimeOrderedEpoch(),
                    "cloud-payload",
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
            return null;
        });

        List<OutboxEventJpaEntity> rows = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
        });
        assertThat(rows).hasSize(1);

        String payload = rows.getFirst().getPayload();
        JsonNode cloudEvent = objectMapper.readTree(payload);

        assertThat(cloudEvent.get("specversion").asString()).isEqualTo("1.0");
        assertThat(cloudEvent.get("type").asString())
                .isEqualTo("io.github.smiskinext.tenant.v1.installed");
        assertThat(cloudEvent.get("datacontenttype").asString()).isEqualTo("application/json");
        assertThat(cloudEvent.get("dataschema").asString())
                .isEqualTo("io.github.smiskinext.event.tenant.v1.TenantInstalled");
        assertThat(cloudEvent.get("id").asString()).isNotBlank();

        JsonNode data = cloudEvent.get("data");
        assertThat(data.get("cloudId").asString()).isEqualTo("cloud-payload");
        assertThat(data.get("installationId").asString()).isEqualTo("install-1");
        assertThat(data.get("appId").asString()).isEqualTo("app-1");
    }

    @Test
    void unmapped_event_type_is_rejected_and_writes_no_row() {
        PublishableEvent unmappedEvent = new PublishableEvent() {
            @Override
            public UUID eventId() {
                return UuidCreator.getTimeOrderedEpoch();
            }

            @Override
            public String aggregateId() {
                return "cloud-unknown";
            }

            @Override
            public String aggregateType() {
                return "unknown";
            }

            @Override
            public String eventType() {
                return "unknown.event";
            }

            @Override
            public String topic() {
                return "unknown.topic";
            }

            @Override
            public Instant occurredAt() {
                return Instant.now();
            }
        };

        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
                    outboxEventPublisher.publish(unmappedEvent);
                    return null;
                }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No OutboxEventProtoMapper registered");

        List<OutboxEventJpaEntity> rows = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
        });
        assertThat(rows).isEmpty();
    }

    @Test
    void uninstall_event_enqueued_with_correct_topic_and_type() {
        transactionTemplate.execute(status -> {
            Instant uninstalledAt = Instant.now();
            Instant purgeAfter = uninstalledAt.plusSeconds(86400 * 30);
            TenantUninstalledEvent event = new TenantUninstalledEvent(
                    UuidCreator.getTimeOrderedEpoch(),
                    "cloud-payload",
                    "install-1",
                    "app-1",
                    uninstalledAt,
                    purgeAfter,
                    null,
                    null,
                    null,
                    null,
                    TenantStatus.UNINSTALLED,
                    Instant.now(),
                    Instant.now());

            outboxEventPublisher.publish(event);
            return null;
        });

        List<OutboxEventJpaEntity> rows = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
        });
        assertThat(rows).hasSize(1);

        OutboxEventJpaEntity row = rows.getFirst();
        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getTenantId()).isEqualTo("cloud-payload");
        assertThat(row.getAggregateId()).isEqualTo("cloud-payload");
        assertThat(row.getTopic()).isEqualTo("tenant.tenant.uninstalled");
        assertThat(row.getEventType()).isEqualTo("io.github.smiskinext.tenant.v1.uninstalled");
    }

    @Test
    void uninstall_event_payload_contains_proto_json_data() throws Exception {
        Instant uninstalledAt = Instant.parse("2025-06-15T10:30:00Z");
        Instant purgeAfter = Instant.parse("2025-07-15T10:30:00Z");

        transactionTemplate.execute(status -> {
            TenantUninstalledEvent event = new TenantUninstalledEvent(
                    UuidCreator.getTimeOrderedEpoch(),
                    "cloud-payload",
                    "install-1",
                    "app-1",
                    uninstalledAt,
                    purgeAfter,
                    null,
                    null,
                    null,
                    null,
                    TenantStatus.UNINSTALLED,
                    Instant.now(),
                    Instant.now());

            outboxEventPublisher.publish(event);
            return null;
        });

        List<OutboxEventJpaEntity> rows = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
        });
        assertThat(rows).hasSize(1);

        String payload = rows.getFirst().getPayload();
        JsonNode cloudEvent = objectMapper.readTree(payload);

        assertThat(cloudEvent.get("type").asString())
                .isEqualTo("io.github.smiskinext.tenant.v1.uninstalled");
        assertThat(cloudEvent.get("dataschema").asString())
                .isEqualTo("io.github.smiskinext.event.tenant.v1.TenantUninstalled");

        JsonNode data = cloudEvent.get("data");
        assertThat(data.get("cloudId").asString()).isEqualTo("cloud-payload");
        assertThat(data.get("installationId").asString()).isEqualTo("install-1");
        assertThat(data.get("appId").asString()).isEqualTo("app-1");
        assertThat(data.get("uninstalledAt").asString()).isEqualTo(uninstalledAt.toString());
        assertThat(data.get("purgeAfter").asString()).isEqualTo(purgeAfter.toString());
    }
}
