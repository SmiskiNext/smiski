package io.github.smiskinext.meet.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.cloudevents.kafka.CloudEventDeserializer;
import io.cloudevents.kafka.CloudEventSerializer;
import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import io.github.smiskinext.meet.infrastructure.persistence.TenantJpaRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
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
class TenantConsumerDltIntegrationTest {

    private static final String INSTALLED_TOPIC = "tenant.tenant.installed";
    private static final String INSTALLED_DLT = "tenant.tenant.installed.dlt";
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private TenantJpaRepository tenantJpaRepository;

    @Autowired
    private KafkaListenerEndpointRegistry endpointRegistry;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private KafkaProducer<String, CloudEvent> producer;
    private KafkaConsumer<String, CloudEvent> dltConsumer;

    @BeforeEach
    void setUp() {
        await().atMost(AWAIT_TIMEOUT)
                .until(() -> endpointRegistry.getListenerContainers().stream()
                        .allMatch(TenantConsumerDltIntegrationTest::hasAssignment));
        dltConsumer = buildDltConsumer();
    }

    @AfterEach
    void tearDown() {
        if (producer != null) {
            producer.close();
        }
        if (dltConsumer != null) {
            dltConsumer.close();
        }
        tenantJpaRepository.deleteAll();
    }

    @Test
    void malformedMessageIsRoutedToDltAndPartitionRemainsUnblocked() {
        String malformedKey = "bad-" + UUID.randomUUID();
        publishRaw(INSTALLED_TOPIC, malformedKey, "{invalid json}");

        String validCloudId = "cloud-dlt-valid-" + UUID.randomUUID();
        String validData = """
                {"cloudId":"%s","updatedAt":"%s","status":"ACTIVE"}""".formatted(validCloudId, OffsetDateTime.now());
        publish(INSTALLED_TOPIC, validCloudId, validData);

        await().atMost(AWAIT_TIMEOUT)
                .untilAsserted(() ->
                        assertThat(tenantJpaRepository.findById(validCloudId)).isPresent());

        dltConsumer.subscribe(java.util.List.of(INSTALLED_DLT));
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            var records = dltConsumer.poll(Duration.ofMillis(500));
            boolean dltReceived = false;
            for (ConsumerRecord<String, CloudEvent> r : records) {
                if (malformedKey.equals(r.key())) {
                    dltReceived = true;
                    break;
                }
            }
            assertThat(dltReceived).as("malformed message should arrive on DLT").isTrue();
        });
    }

    private void publish(String topic, String key, String dataJson) {
        CloudEvent event = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType("io.github.smiskinext.tenant.v1.installed")
                .withSource(URI.create("tenant-service"))
                .withDataContentType("application/json")
                .withTime(OffsetDateTime.now())
                .withData(
                        "application/json",
                        BytesCloudEventData.wrap(dataJson.getBytes(StandardCharsets.UTF_8)))
                .build();
        producer().send(new ProducerRecord<>(topic, key, event));
        producer().flush();
    }

    private void publishRaw(String topic, String key, String rawPayload) {
        CloudEvent event = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType("io.github.smiskinext.tenant.v1.installed")
                .withSource(URI.create("tenant-service"))
                .withDataContentType("application/json")
                .withTime(OffsetDateTime.now())
                .withData(
                        "application/json",
                        BytesCloudEventData.wrap(rawPayload.getBytes(StandardCharsets.UTF_8)))
                .build();
        producer().send(new ProducerRecord<>(topic, key, event));
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

    private KafkaConsumer<String, CloudEvent> buildDltConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                CloudEventDeserializer.class.getName());
        return new KafkaConsumer<>(props);
    }

    private static boolean hasAssignment(MessageListenerContainer container) {
        return container.getAssignedPartitions() != null
                && !container.getAssignedPartitions().isEmpty();
    }
}
