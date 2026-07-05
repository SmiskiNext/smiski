package io.github.smiskinext.meetingmanagement;

import static org.assertj.core.api.Assertions.assertThat;

import io.cloudevents.CloudEvent;
import io.github.smiskinext.meetingmanagement.config.TestcontainersConfiguration;
import io.github.smiskinext.meetingmanagement.infrastructure.jobs.JoinRequestCleanupJob;
import io.github.smiskinext.meetingmanagement.infrastructure.jobs.RecordingCleanupJob;
import io.github.smiskinext.meetingmanagement.infrastructure.messaging.KafkaEventPublisher;
import io.github.smiskinext.meetingmanagement.infrastructure.messaging.OutboxEventPublisher;
import io.github.smiskinext.meetingmanagement.infrastructure.messaging.UserProfileUpdatedConsumer;
import io.github.smiskinext.meetingmanagement.infrastructure.sse.MeetingSseManager;
import io.github.phunguy65.zms.proto.user.v1.UserServiceGrpc;
import io.github.smiskinext.shared.domain.CursorTokenEncoder;
import io.livekit.server.EgressServiceClient;
import io.livekit.server.RoomServiceClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Boots the full Spring context on a random port and fetches the springdoc OpenAPI YAML,
 * writing the result to {@code build/openapi/openapi.yaml} for downstream pipeline consumption.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OpenApiGenerationTest {

    @LocalServerPort
    int port;

    // ── Mock Kafka infrastructure ───────────────────────────────────────────────

    @MockitoBean
    ProducerFactory<String, CloudEvent> producerFactory;

    @MockitoBean
    KafkaTemplate<String, CloudEvent> kafkaTemplate;

    @MockitoBean
    ConsumerFactory<String, CloudEvent> consumerFactory;

    @MockitoBean
    ConcurrentKafkaListenerContainerFactory<String, CloudEvent> kafkaListenerContainerFactory;

    @MockitoBean
    KafkaEventPublisher kafkaEventPublisher;

    @MockitoBean
    OutboxEventPublisher outboxEventPublisher;

    @MockitoBean
    UserProfileUpdatedConsumer userProfileUpdatedConsumer;

    @MockitoBean
    MeetingSseManager meetingSseManager;

    // ── Mock scheduled jobs ─────────────────────────────────────────────────────

    @MockitoBean
    RecordingCleanupJob recordingCleanupJob;

    @MockitoBean
    JoinRequestCleanupJob joinRequestCleanupJob;

    // ── Mock LiveKit ────────────────────────────────────────────────────────────

    @MockitoBean
    RoomServiceClient roomServiceClient;

    @MockitoBean
    EgressServiceClient egressServiceClient;

    // ── Mock gRPC client ────────────────────────────────────────────────────────

    @MockitoBean
    UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub;

    // ── Mock shared beans not auto-scanned ──────────────────────────────────────

    @MockitoBean
    CursorTokenEncoder cursorTokenEncoder;

    // ── Test ────────────────────────────────────────────────────────────────────

    @Test
    void generateOpenApiSpec() throws IOException, InterruptedException {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v3/api-docs.yaml"))
                .GET()
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isNotBlank();

        Path outputDir = Path.of("build/openapi");
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("openapi.yaml"), response.body());
    }
}
