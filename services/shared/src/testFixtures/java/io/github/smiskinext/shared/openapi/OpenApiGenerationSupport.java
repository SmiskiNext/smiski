package io.github.smiskinext.shared.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Reusable base for per-service OpenAPI generation tests.
 *
 * <p>Boots the concrete service via {@code @SpringBootTest(webEnvironment = RANDOM_PORT)} in the
 * subclass, fetches the springdoc {@code /v3/api-docs.yaml} document and writes it to the location
 * given by the {@code openapi.output.file} system property (falling back to {@code openapi.yaml} in
 * the working directory). The {@code generateOpenApiDocsFromTests} Gradle task supplies an absolute
 * path pointing at each service's project root so the spec is committed alongside its source.
 *
 * <p>Concrete subclasses declare the service context and any {@code @MockitoBean} overrides required
 * to boot without external infrastructure. Their name must end with {@code OpenApiGenerationTest} to
 * match the task's test filter.
 */
public abstract class OpenApiGenerationSupport {

    private static final String OUTPUT_FILE_PROPERTY = "openapi.output.file";
    private static final String DEFAULT_OUTPUT_FILE = "openapi.yaml";
    private static final String API_DOCS_PATH = "/v3/api-docs.yaml";

    @LocalServerPort
    protected int port;

    @Test
    void generateOpenApiSpec() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + API_DOCS_PATH))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isNotBlank();

        Path output = Path.of(System.getProperty(OUTPUT_FILE_PROPERTY, DEFAULT_OUTPUT_FILE))
                .toAbsolutePath();
        Files.createDirectories(output.getParent());
        Files.writeString(output, response.body());
    }
}
