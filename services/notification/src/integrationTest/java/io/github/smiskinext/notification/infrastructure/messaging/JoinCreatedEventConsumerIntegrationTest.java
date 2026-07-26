package io.github.smiskinext.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.cloudevents.kafka.CloudEventSerializer;
import io.github.smiskinext.notification.config.TestcontainersConfiguration;
import io.github.smiskinext.notification.domain.model.PendingJoinRequest;
import io.github.smiskinext.notification.domain.port.PendingJoinRequestStore;
import io.github.smiskinext.notification.support.KafkaListenerReadySupport;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
class JoinCreatedEventConsumerIntegrationTest {

    private static final String TOPIC = "meet.join.created";
    private static final String TYPE = "io.github.smiskinext.meet.join.created.v1";

    @Autowired
    private PendingJoinRequestStore pendingJoinRequestStore;

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
    void consumedEventIsStoredForReplay() {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        String data = """
                {"meetingId":"%s","joinRequestId":"%s","tenantId":"tenant-1",\
                "accountId":"account-1","displayName":"Alice","deviceId":"device-1",\
                "occurredAt":"%s","avatarUrl":"https://cdn.example.com/a.png"}""".formatted(meetingId, requestId, Instant.now().toString());

        publish(meetingId.toString(), data);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<PendingJoinRequest> pending =
                    pendingJoinRequestStore.findPendingByMeetingId(meetingId);
            assertThat(pending).hasSize(1);
            PendingJoinRequest request = pending.getFirst();
            assertThat(request.joinRequestId()).isEqualTo(requestId);
            assertThat(request.accountId()).isEqualTo("account-1");
            assertThat(request.displayName()).isEqualTo("Alice");
        });
    }

    @Test
    void malformedEventDoesNotBreakConsumer() {
        UUID meetingId = UUID.randomUUID();
        publish(meetingId.toString(), "{\"not\":\"a-join\"}");

        UUID requestId = UUID.randomUUID();
        String valid = """
                {"meetingId":"%s","joinRequestId":"%s","tenantId":"tenant-1",\
                "accountId":"account-1","displayName":"Alice","deviceId":"device-1",\
                "occurredAt":"%s","avatarUrl":""}""".formatted(meetingId, requestId, Instant.now().toString());
        publish(meetingId.toString(), valid);

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () -> assertThat(pendingJoinRequestStore.findPendingByMeetingId(meetingId))
                                .anyMatch(request -> request.joinRequestId().equals(requestId)));
    }

    private void publish(String key, String dataJson) {
        CloudEvent event = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(TYPE)
                .withSource(URI.create("/meet"))
                .withDataContentType("application/json")
                .withTime(OffsetDateTime.now())
                .withSubject(key)
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
}
