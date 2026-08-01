package io.github.smiskinext.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.cloudevents.kafka.CloudEventSerializer;
import io.github.smiskinext.notification.config.TestcontainersConfiguration;
import io.github.smiskinext.notification.domain.model.CalendarEmail;
import io.github.smiskinext.notification.domain.port.EmailSender;
import io.github.smiskinext.notification.support.KafkaListenerReadySupport;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
class MeetingInfoUpdatedEmailConsumerIntegrationTest {

    private static final String TOPIC = "meet.meeting.info.updated";
    private static final String TYPE = "io.github.smiskinext.meet.meeting.info.updated.v1";

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
            producer = null;
        }
    }

    @Test
    void timeChangeSendsEmailPerInvitee() {
        publish(timeChangeWithInviteesJson());

        ArgumentCaptor<CalendarEmail> captor = ArgumentCaptor.forClass(CalendarEmail.class);
        verify(emailSender, timeout(30_000).times(2)).send(captor.capture());
        List<String> recipients =
                captor.getAllValues().stream().map(CalendarEmail::recipient).toList();
        assertThat(recipients).containsExactlyInAnyOrder("bob@test.com", "carol@test.com");
        assertThat(captor.getAllValues())
                .allSatisfy(email -> assertThat(email.calendarMethod()).isEqualTo("REQUEST"));
    }

    @Test
    void nonTimeChangeProducesNoEmail() {
        publish(nonTimeChangeJson());

        publish(timeChangeWithInviteesJson());
        verify(emailSender, timeout(30_000).times(2)).send(any(CalendarEmail.class));
    }

    @Test
    void malformedEventIsSkippedAndSubsequentEventsStillProcess() {
        publish("{\"not\":\"valid-proto\"}");

        publish(timeChangeWithInviteesJson());
        verify(emailSender, timeout(30_000).times(2)).send(any(CalendarEmail.class));
    }

    private static String timeChangeWithInviteesJson() {
        return """
                {"meetingId":"%s","tenantId":"tenant-1","hostId":"host-1","updatedBy":"host-1",\
                "status":"SCHEDULED",\
                "oldInfo":{"title":"Sprint","description":"desc","zoneId":"UTC",\
                "startTime":"2026-01-01T10:00:00Z","endTime":"2026-01-01T11:00:00Z",\
                "organizerEmail":"host@example.com","organizerDisplayName":"Host",\
                "calendarUid":"uid-123","calendarSequence":1,\
                "issueLink":{"issueId":"1","issueKey":"PROJ-1","projectKey":"PROJ"}},\
                "newInfo":{"title":"Sprint","description":"desc","zoneId":"UTC",\
                "startTime":"2026-01-01T12:00:00Z","endTime":"2026-01-01T13:00:00Z",\
                "organizerEmail":"host@example.com","organizerDisplayName":"Host",\
                "calendarUid":"uid-123","calendarSequence":2,\
                "issueLink":{"issueId":"1","issueKey":"PROJ-1","projectKey":"PROJ"}},\
                "updatedAt":"2026-01-01T09:00:00Z",\
                "invitees":[{"accountId":"a1","email":"bob@test.com","displayName":"Bob","inviteeId":"%s","status":"NEEDS_ACTION"},\
                {"accountId":"a2","email":"carol@test.com","displayName":"Carol","inviteeId":"%s","status":"ACCEPTED"}]}""".formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private static String nonTimeChangeJson() {
        return """
                {"meetingId":"%s","tenantId":"tenant-1","hostId":"host-1","updatedBy":"host-1",\
                "status":"SCHEDULED",\
                "oldInfo":{"title":"Sprint","description":"old desc","zoneId":"UTC",\
                "startTime":"2026-01-01T10:00:00Z","endTime":"2026-01-01T11:00:00Z",\
                "organizerEmail":"host@example.com","organizerDisplayName":"Host",\
                "calendarUid":"uid-123","calendarSequence":1,\
                "issueLink":{"issueId":"1","issueKey":"PROJ-1","projectKey":"PROJ"}},\
                "newInfo":{"title":"Sprint Planning","description":"new desc","zoneId":"UTC",\
                "startTime":"2026-01-01T10:00:00Z","endTime":"2026-01-01T11:00:00Z",\
                "organizerEmail":"host@example.com","organizerDisplayName":"Host",\
                "calendarUid":"uid-123","calendarSequence":2,\
                "issueLink":{"issueId":"1","issueKey":"PROJ-1","projectKey":"PROJ"}},\
                "updatedAt":"2026-01-01T09:00:00Z",\
                "invitees":[{"accountId":"a1","email":"bob@test.com","displayName":"Bob","inviteeId":"%s","status":"NEEDS_ACTION"}]}""".formatted(UUID.randomUUID(), UUID.randomUUID());
    }

    private void publish(String dataJson) {
        CloudEvent event = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(TYPE)
                .withSource(URI.create("/meet"))
                .withDataContentType("application/json")
                .withTime(OffsetDateTime.now())
                .withSubject(UUID.randomUUID().toString())
                .withData(
                        "application/json",
                        BytesCloudEventData.wrap(dataJson.getBytes(StandardCharsets.UTF_8)))
                .build();
        producer().send(new ProducerRecord<>(TOPIC, UUID.randomUUID().toString(), event));
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
