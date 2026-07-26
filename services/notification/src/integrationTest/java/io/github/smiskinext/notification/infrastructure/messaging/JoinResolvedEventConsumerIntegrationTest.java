package io.github.smiskinext.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.cloudevents.kafka.CloudEventSerializer;
import io.github.smiskinext.notification.config.TestcontainersConfiguration;
import io.github.smiskinext.notification.domain.model.JoinDecision;
import io.github.smiskinext.notification.domain.port.JoinDecisionStore;
import io.github.smiskinext.notification.support.KafkaListenerReadySupport;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
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
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class JoinResolvedEventConsumerIntegrationTest {

    private static final String APPROVED_TOPIC = "meet.join.approved";
    private static final String DENIED_TOPIC = "meet.join.denied";

    @Autowired
    private JoinDecisionStore joinDecisionStore;

    @Autowired
    private KafkaListenerEndpointRegistry endpointRegistry;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private KafkaProducer<String, CloudEvent> producer;

    @BeforeEach
    void awaitConsumerAssignment() {
        KafkaListenerReadySupport.awaitAllContainersAssigned(endpointRegistry);
    }

    @AfterEach
    void tearDown() {
        if (producer != null) {
            producer.close();
        }
    }

    @Test
    void approvedEventIsStoredForReplay() {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        String data = """
                {"meetingId":"%s","joinRequestId":"%s","tenantId":"tenant-1",\
                "accountId":"account-1","deviceId":"device-1","liveKitToken":"the-token",\
                "roomName":"meeting-%s","approvedBy":"host","occurredAt":"2026-01-01T00:00:00Z"}""".formatted(meetingId, requestId, meetingId);

        publish(
                APPROVED_TOPIC,
                meetingId.toString(),
                "io.github.smiskinext.meet.join.approved.v1",
                data);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Optional<JoinDecision> stored = joinDecisionStore.findByRequestId(requestId);
            assertThat(stored).isPresent();
            assertThat(stored.get().status()).isEqualTo(JoinDecision.Status.APPROVED);
            assertThat(stored.get().token()).isEqualTo("the-token");
            assertThat(stored.get().roomName()).isEqualTo("meeting-" + meetingId);
        });
    }

    @Test
    void deniedEventIsStoredForReplay() {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        String data = """
                {"meetingId":"%s","joinRequestId":"%s","tenantId":"tenant-1",\
                "accountId":"account-1","deviceId":"device-1","deniedBy":"host",\
                "occurredAt":"2026-01-01T00:00:00Z"}""".formatted(meetingId, requestId);

        publish(
                DENIED_TOPIC,
                meetingId.toString(),
                "io.github.smiskinext.meet.join.denied.v1",
                data);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Optional<JoinDecision> stored = joinDecisionStore.findByRequestId(requestId);
            assertThat(stored).isPresent();
            assertThat(stored.get().status()).isEqualTo(JoinDecision.Status.DENIED);
            assertThat(stored.get().token()).isNull();
        });
    }

    @Test
    void malformedEventDoesNotBreakConsumer() {
        UUID meetingId = UUID.randomUUID();
        publish(
                APPROVED_TOPIC,
                meetingId.toString(),
                "io.github.smiskinext.meet.join.approved.v1",
                "{\"not\":\"a-decision\"}");

        UUID requestId = UUID.randomUUID();
        String valid = """
                {"meetingId":"%s","joinRequestId":"%s","tenantId":"tenant-1",\
                "accountId":"account-1","deviceId":"device-1","liveKitToken":"tok",\
                "roomName":"meeting-%s","approvedBy":"host","occurredAt":"2026-01-01T00:00:00Z"}""".formatted(meetingId, requestId, meetingId);
        publish(
                APPROVED_TOPIC,
                meetingId.toString(),
                "io.github.smiskinext.meet.join.approved.v1",
                valid);

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() ->
                        assertThat(joinDecisionStore.findByRequestId(requestId)).isPresent());
    }

    private void publish(String topic, String key, String type, String dataJson) {
        CloudEvent event = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(type)
                .withSource(URI.create("/meet"))
                .withDataContentType("application/json")
                .withTime(OffsetDateTime.now())
                .withSubject(key)
                .withData(
                        "application/json",
                        BytesCloudEventData.wrap(dataJson.getBytes(StandardCharsets.UTF_8)))
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
}
