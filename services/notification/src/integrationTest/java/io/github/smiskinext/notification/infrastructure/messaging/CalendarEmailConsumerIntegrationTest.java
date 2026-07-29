package io.github.smiskinext.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.cloudevents.kafka.CloudEventDeserializer;
import io.cloudevents.kafka.CloudEventSerializer;
import io.github.smiskinext.notification.config.TestcontainersConfiguration;
import io.github.smiskinext.notification.domain.model.CalendarEmail;
import io.github.smiskinext.notification.domain.port.EmailSender;
import io.github.smiskinext.notification.infrastructure.email.EmailDeliveryException;
import io.github.smiskinext.notification.support.KafkaListenerReadySupport;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CalendarEmailConsumerIntegrationTest {

    private static final String INVITATIONS_TOPIC = "meet.meeting.invitations.created";
    private static final String ACCEPTED_TOPIC = "meet.invitee.accepted";
    private static final String TENTATIVE_TOPIC = "meet.invitee.tentative";

    @MockitoBean
    private EmailSender emailSender;

    @Autowired
    private KafkaListenerEndpointRegistry endpointRegistry;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private KafkaProducer<String, CloudEvent> producer;

    @BeforeEach
    void awaitConsumerAssignment() {
        Mockito.reset(emailSender);
        KafkaListenerReadySupport.awaitAllContainersAssigned(endpointRegistry);
    }

    @AfterEach
    void tearDown() {
        if (producer != null) {
            producer.close();
        }
    }

    @Test
    void invitationEventSendsOneInviteEmailPerInvitee() {
        String uid = "uid-" + UUID.randomUUID();
        String data = """
                {"meetingId":"%s","tenantId":"tenant-1","meetingTitle":"Sprint",\
                "meetingShortCode":"ABC123","startTime":"2026-01-01T10:00:00Z",\
                "endTime":"2026-01-01T11:00:00Z","zoneId":"UTC",\
                "organizerEmail":"host@example.com","organizerDisplayName":"Host",\
                "calendarUid":"%s","calendarSequence":1,\
                "invitees":[{"accountId":"a1","email":"bob@test.com","displayName":"Bob","inviteeId":"%s","status":"NEEDS_ACTION"},\
                {"accountId":"a2","email":"carol@test.com","displayName":"Carol","inviteeId":"%s","status":"NEEDS_ACTION"}]}""".formatted(UUID.randomUUID(), uid, UUID.randomUUID(), UUID.randomUUID());

        publish(
                INVITATIONS_TOPIC,
                UUID.randomUUID().toString(),
                "io.github.smiskinext.meet.meeting.invitations.created.v1",
                data);

        ArgumentCaptor<CalendarEmail> captor = ArgumentCaptor.forClass(CalendarEmail.class);
        verify(emailSender, timeout(30_000).times(2)).send(captor.capture());
        List<String> recipients =
                captor.getAllValues().stream().map(CalendarEmail::recipient).toList();
        assertThat(recipients).containsExactlyInAnyOrder("bob@test.com", "carol@test.com");
        assertThat(captor.getAllValues())
                .allSatisfy(email -> assertThat(email.calendarMethod()).isEqualTo("REQUEST"));
    }

    @Test
    void acceptedResponseSendsOrganizerReplyEmail() {
        String uid = "uid-" + UUID.randomUUID();
        String data = responseData(uid, "ACCEPTED");

        publish(
                ACCEPTED_TOPIC,
                UUID.randomUUID().toString(),
                "io.github.smiskinext.meet.invitee.accepted.v1",
                data);

        ArgumentCaptor<CalendarEmail> captor = ArgumentCaptor.forClass(CalendarEmail.class);
        verify(emailSender, timeout(30_000)).send(captor.capture());
        CalendarEmail email = captor.getValue();
        assertThat(email.recipient()).isEqualTo("host@example.com");
        assertThat(email.calendarMethod()).isEqualTo("REPLY");
        assertThat(email.icsContent()).contains("PARTSTAT=ACCEPTED");
    }

    @Test
    void tentativeResponseSendsReplyWithTentativePartStat() {
        String uid = "uid-" + UUID.randomUUID();
        String data = responseData(uid, "TENTATIVE");

        publish(
                TENTATIVE_TOPIC,
                UUID.randomUUID().toString(),
                "io.github.smiskinext.meet.invitee.tentative.v1",
                data);

        ArgumentCaptor<CalendarEmail> captor = ArgumentCaptor.forClass(CalendarEmail.class);
        verify(emailSender, timeout(30_000)).send(captor.capture());
        assertThat(captor.getValue().icsContent()).contains("PARTSTAT=TENTATIVE");
    }

    @Test
    void malformedInvitationEventIsSkippedWithoutSending() {
        publish(
                INVITATIONS_TOPIC,
                UUID.randomUUID().toString(),
                "io.github.smiskinext.meet.meeting.invitations.created.v1",
                "{\"not\":\"an-invitation\"}");

        String uid = "uid-" + UUID.randomUUID();
        publish(
                ACCEPTED_TOPIC,
                UUID.randomUUID().toString(),
                "io.github.smiskinext.meet.invitee.accepted.v1",
                responseData(uid, "ACCEPTED"));

        verify(emailSender, timeout(30_000).times(1)).send(any(CalendarEmail.class));
    }

    @Test
    void persistentSendFailureRoutesToDeadLetterTopic() {
        doThrow(new EmailDeliveryException("boom", new RuntimeException()))
                .when(emailSender)
                .send(any(CalendarEmail.class));

        String uid = "uid-" + UUID.randomUUID();
        publish(
                ACCEPTED_TOPIC,
                UUID.randomUUID().toString(),
                "io.github.smiskinext.meet.invitee.accepted.v1",
                responseData(uid, "ACCEPTED"));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() ->
                        assertThat(pollDeadLetter(ACCEPTED_TOPIC + ".dlt")).isNotEmpty());
    }

    private static String responseData(String uid, String status) {
        return """
                {"eventId":"%s","tenantId":"tenant-1","meetingId":"%s","inviterId":"host",\
                "inviteeId":"%s","inviteeEmail":"bob@test.com","status":"%s",\
                "%s":"2026-01-01T09:00:00Z","meetingTitle":"Sprint",\
                "startTime":"2026-01-01T10:00:00Z","endTime":"2026-01-01T11:00:00Z","zoneId":"UTC",\
                "organizerEmail":"host@example.com","organizerDisplayName":"Host",\
                "inviteeDisplayName":"Bob","calendarUid":"%s","calendarSequence":1}""".formatted(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        status,
                        timestampField(status),
                        uid);
    }

    private static String timestampField(String status) {
        return switch (status) {
            case "ACCEPTED" -> "acceptedAt";
            case "DECLINED" -> "declinedAt";
            default -> "tentativeAt";
        };
    }

    private List<ConsumerRecord<String, CloudEvent>> pollDeadLetter(String topic) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-probe-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, CloudEventDeserializer.class);
        List<ConsumerRecord<String, CloudEvent>> collected = new ArrayList<>();
        try (KafkaConsumer<String, CloudEvent> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            ConsumerRecords<String, CloudEvent> records = consumer.poll(Duration.ofSeconds(2));
            records.forEach(collected::add);
        }
        return collected;
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
