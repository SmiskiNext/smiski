package io.github.smiskinext.meet.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.cloudevents.CloudEvent;
import io.cloudevents.kafka.CloudEventDeserializer;
import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxRelay;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verifies that {@code SseTriggeringEvent} join requests are relayed to Kafka near-immediately after
 * commit (well under the scheduled poll interval, which the {@code test} profile widens to 60s so
 * any delivery inside the assertion bound is provably kick-driven, never poll-driven).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SseRelayImmediateDeliveryIntegrationTest {

    private static final String TENANT_ID = "tenant-test";
    private static final String JOIN_TOPIC = "meet.join.created";
    private static final Duration IMMEDIATE_BOUND = Duration.ofSeconds(2);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private KafkaConsumer<String, CloudEvent> consumer;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO tenants (tenant_id, cloud_id, status, updated_at)
                VALUES (?, ?, 'ACTIVE', NOW())
                ON CONFLICT (tenant_id) DO NOTHING
                """, TENANT_ID, TENANT_ID);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "meet-sse-relay-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, CloudEventDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(JOIN_TOPIC));
        awaitAssignment();
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void manualApprovalJoinReachesKafkaWellUnderPollInterval() throws Exception {
        UUID meetingId = insertMeeting("MANUAL_APPROVAL");

        long start = System.nanoTime();
        performJoin(meetingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        Optional<ConsumerRecord<String, CloudEvent>> received =
                pollFor(meetingId.toString(), IMMEDIATE_BOUND);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(received).isPresent();
        assertThat(received.get().value().getType())
                .isEqualTo("io.github.smiskinext.meet.join.created.v1");
        assertThat(elapsed).isLessThan(IMMEDIATE_BOUND);
    }

    @Test
    void rolledBackJoinTransactionWritesNoOutboxRowAndPublishesNothing() {
        UUID meetingId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(txStatus -> {
            jdbcTemplate.update(
                    "INSERT INTO outbox_event (tenant_id, aggregate_id, aggregate_type, event_type, topic, payload)"
                            + " VALUES (?, ?::uuid, 'meeting', 'io.github.smiskinext.meet.join.created.v1', ?, 'payload')",
                    TENANT_ID,
                    meetingId.toString(),
                    JOIN_TOPIC);
            txStatus.setRollbackOnly();
        });

        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ?::uuid",
                Long.class,
                meetingId.toString());
        assertThat(rows).isZero();

        assertThat(pollFor(meetingId.toString(), IMMEDIATE_BOUND)).isEmpty();
    }

    @Test
    void immediatelyPublishedRowIsNotResentBySubsequentPoll() throws Exception {
        UUID meetingId = insertMeeting("MANUAL_APPROVAL");

        performJoin(meetingId).andExpect(status().isOk());

        assertThat(pollFor(meetingId.toString(), IMMEDIATE_BOUND)).isPresent();

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            Long published = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ?::uuid AND published_at IS NOT NULL",
                    Long.class,
                    meetingId.toString());
            assertThat(published).isEqualTo(1L);
        });

        outboxRelay.relay();

        assertThat(pollFor(meetingId.toString(), IMMEDIATE_BOUND)).isEmpty();

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ?::uuid",
                Long.class,
                meetingId.toString());
        assertThat(total).isEqualTo(1L);
    }

    private org.springframework.test.web.servlet.ResultActions performJoin(UUID meetingId)
            throws Exception {
        String requestBody = """
                {"displayName": "Alice", "deviceId": "device-1"}
                """;
        return mockMvc.perform(post("/api/1/meetings/{id}:join", meetingId)
                .header("X-Project-Permissions", "view-meeting,edit-meeting")
                .header("X-Account-Id", "participant-1")
                .header("X-Tenant-ID", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));
    }

    private void awaitAssignment() {
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            consumer.poll(Duration.ofMillis(200));
            return !consumer.assignment().isEmpty();
        });
    }

    private Optional<ConsumerRecord<String, CloudEvent>> pollFor(String key, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, CloudEvent> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, CloudEvent> record : records) {
                if (key.equals(record.key())) {
                    return Optional.of(record);
                }
            }
        }
        return Optional.empty();
    }

    private UUID insertMeeting(String admissionPolicy) {
        UUID id = UUID.randomUUID();
        String settings = """
                {"admissionPolicy": "%s", "maxParticipants": 50, "allowScreenShare": true, \
                "chatEnabled": true, "allowMicrophone": true, "allowVideo": true}
                """.formatted(admissionPolicy);
        jdbcTemplate.update(
                """
                INSERT INTO meetings (
                    tenant_id, id, host_id, organizer_email, organizer_display_name,
                    calendar_uid, calendar_sequence, short_code, issue_id, issue_key,
                    project_key, title, description, zone_id, type, status, settings
                ) VALUES (?, ?, 'host', 'host@example.com', 'Host User', ?, 0, ?,
                    'ISS-1', 'PROJ-1', 'PROJ', 'Title', 'Description', 'UTC',
                    'INSTANT', 'RUNNING', ?::jsonb)
                """,
                TENANT_ID,
                id,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString().substring(0, 12),
                settings);
        return id;
    }
}
