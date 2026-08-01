package io.github.smiskinext.meet.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.cloudevents.kafka.CloudEventSerializer;
import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import io.github.smiskinext.meet.domain.model.TenantStatus;
import io.github.smiskinext.meet.infrastructure.persistence.TenantJpaEntity;
import io.github.smiskinext.meet.infrastructure.persistence.TenantJpaRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TenantUninstalledEventConsumerIntegrationTest {

    private static final String TOPIC = "tenant.tenant.uninstalled";
    private static final String TYPE = "io.github.smiskinext.tenant.v1.uninstalled";
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private TenantJpaRepository tenantJpaRepository;

    @Autowired
    private KafkaListenerEndpointRegistry endpointRegistry;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private KafkaProducer<String, CloudEvent> producer;

    @BeforeEach
    void awaitConsumerAssignment() {
        await().atMost(AWAIT_TIMEOUT)
                .until(() -> endpointRegistry.getListenerContainers().stream()
                        .allMatch(TenantUninstalledEventConsumerIntegrationTest::hasAssignment));
    }

    @AfterEach
    void tearDown() {
        if (producer != null) {
            producer.close();
        }
        tenantJpaRepository.deleteAll();
    }

    @Test
    void consumedUninstalledEventUpsertsUninstalledRow() {
        String cloudId = "cloud-uninstall-" + UUID.randomUUID();
        Instant uninstalledAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant purgeAfter = uninstalledAt.plus(30, ChronoUnit.DAYS);
        String data = """
                {"cloudId":"%s","uninstalledAt":"%s","purgeAfter":"%s",
                "updatedAt":"%s","status":"UNINSTALLED"}""".formatted(cloudId, uninstalledAt, purgeAfter, uninstalledAt);

        publish(cloudId, data);

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            Optional<TenantJpaEntity> entity = tenantJpaRepository.findById(cloudId);
            assertThat(entity).isPresent();
            assertThat(entity.get().getStatus()).isEqualTo(TenantStatus.UNINSTALLED.name());
            assertThat(entity.get().getUninstalledAt()).isNotNull();
            assertThat(entity.get().getPurgeAfter()).isNotNull();
        });
    }

    @Test
    void redeliveredUninstalledEventIsIdempotent() {
        String cloudId = "cloud-uninstall-idem-" + UUID.randomUUID();
        Instant uninstalledAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String data = """
                {"cloudId":"%s","uninstalledAt":"%s","updatedAt":"%s","status":"UNINSTALLED"}""".formatted(cloudId, uninstalledAt, uninstalledAt);

        publish(cloudId, data);
        publish(cloudId, data);

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            long count = tenantJpaRepository.findAll().stream()
                    .filter(e -> cloudId.equals(e.getTenantId()))
                    .count();
            assertThat(count).isEqualTo(1);
            assertThat(tenantJpaRepository.findById(cloudId))
                    .isPresent()
                    .get()
                    .extracting(TenantJpaEntity::getStatus)
                    .isEqualTo(TenantStatus.UNINSTALLED.name());
        });
    }

    private void publish(String key, String dataJson) {
        CloudEvent event = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(TYPE)
                .withSource(URI.create("tenant-service"))
                .withDataContentType("application/json")
                .withTime(OffsetDateTime.now())
                .withData(
                        "application/json",
                        BytesCloudEventData.wrap(dataJson.getBytes(StandardCharsets.UTF_8)))
                .build();
        producer().send(new ProducerRecord<>(TOPIC, key, event));
        producer().flush();
    }

    private KafkaProducer<String, CloudEvent> producer() {
        if (producer == null) {
            Map<String, Object> props = new HashMap<>();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                    CloudEventSerializer.class.getName());
            producer = new KafkaProducer<>(props);
        }
        return producer;
    }

    private static boolean hasAssignment(MessageListenerContainer container) {
        return container.getAssignedPartitions() != null
                && !container.getAssignedPartitions().isEmpty();
    }
}
