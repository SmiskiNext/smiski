package io.github.smiskinext.tenant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import io.github.smiskinext.tenant.config.TestcontainersConfiguration;
import io.github.smiskinext.tenant.domain.event.TenantInstalledEvent;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class OutboxEventPublisherIntegrationTest {

    @Autowired
    private OutboxEventPublisher outboxEventPublisher;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant("cloud-payload");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void payload_is_cloudevent_with_proto_json_data() throws Exception {
        TenantInstalledEvent event = new TenantInstalledEvent(
                UuidCreator.getTimeOrderedEpoch(),
                "cloud-payload",
                "install-1",
                "app-1",
                "1.0.0",
                null,
                "PRODUCTION",
                null,
                null,
                Instant.now());

        outboxEventPublisher.publish(event);

        List<OutboxEventJpaEntity> rows =
                outboxEventJpaRepository.findUnpublishedOrderByCreatedAt();
        assertThat(rows).hasSize(1);

        String payload = rows.getFirst().getPayload();
        JsonNode cloudEvent = objectMapper.readTree(payload);

        assertThat(cloudEvent.get("specversion").asText()).isEqualTo("1.0");
        assertThat(cloudEvent.get("type").asText())
                .isEqualTo("io.github.smiskinext.tenant.v1.installed");
        assertThat(cloudEvent.get("datacontenttype").asText()).isEqualTo("application/json");
        assertThat(cloudEvent.get("dataschema").asText())
                .isEqualTo("io.github.smiskinext.event.tenant.v1.TenantInstalled");
        assertThat(cloudEvent.get("id").asText()).isNotBlank();

        JsonNode data = cloudEvent.get("data");
        assertThat(data.get("cloudId").asText()).isEqualTo("cloud-payload");
        assertThat(data.get("installationId").asText()).isEqualTo("install-1");
        assertThat(data.get("appId").asText()).isEqualTo("app-1");
    }
}
