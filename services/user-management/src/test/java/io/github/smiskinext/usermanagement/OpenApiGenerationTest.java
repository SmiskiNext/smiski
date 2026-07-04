package io.github.smiskinext.usermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import io.cloudevents.CloudEvent;
import io.github.smiskinext.usermanagement.config.TestcontainersConfiguration;
import io.github.smiskinext.usermanagement.infrastructure.messaging.KafkaEventPublisher;
import io.github.smiskinext.usermanagement.infrastructure.messaging.OutboxEventPublisher;
import io.github.smiskinext.usermanagement.infrastructure.security.FirebaseTokenVerifier;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Boots the full Spring context on a random port and fetches the springdoc OpenAPI YAML,
 * writing the result to {@code build/openapi/openapi.yaml} for downstream pipeline consumption.
 *
 * <p>External infrastructure beans (Kafka, Firebase) are mocked via {@link MockitoBean};
 * the database is provided by Testcontainers (Postgres).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OpenApiGenerationTest {

    @LocalServerPort
    int port;

    // ── Mock external infrastructure ────────────────────────────────────────────

    @MockitoBean
    FirebaseTokenVerifier firebaseTokenVerifier;

    @MockitoBean
    ProducerFactory<String, CloudEvent> producerFactory;

    @MockitoBean
    KafkaTemplate<String, CloudEvent> kafkaTemplate;

    @MockitoBean
    KafkaEventPublisher kafkaEventPublisher;

    @MockitoBean
    OutboxEventPublisher outboxEventPublisher;

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
