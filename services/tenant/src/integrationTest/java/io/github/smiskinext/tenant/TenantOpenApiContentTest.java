package io.github.smiskinext.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.tenant.config.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TenantOpenApiContentTest {

    @LocalServerPort
    private int port;

    @Test
    void spec_contains_problem_detail_schema_with_required_members() throws Exception {
        String spec = fetchSpec();

        assertThat(spec).contains("ProblemDetail:");
        assertThat(spec).contains("Violation:");

        assertThat(spec).containsPattern("(?m)^\\s+type:");
        assertThat(spec).containsPattern("(?m)^\\s+title:");
        assertThat(spec).containsPattern("(?m)^\\s+status:");
        assertThat(spec).containsPattern("(?m)^\\s+detail:");
        assertThat(spec).containsPattern("(?m)^\\s+code:");
        assertThat(spec).containsPattern("(?m)^\\s+traceId:");
        assertThat(spec).containsPattern("(?m)^\\s+errors:");
    }

    @Test
    void spec_documents_post_tenants_with_success_responses() throws Exception {
        String spec = fetchSpec();

        assertThat(spec).contains("\"201\":");
        assertThat(spec).contains("\"200\":");
        assertThat(spec).contains("TenantResponse");
    }

    @Test
    void spec_documents_post_tenants_400_with_examples() throws Exception {
        String spec = fetchSpec();

        assertThat(spec).contains("\"400\":");
        assertThat(spec).contains("VALIDATION_ERROR");
        assertThat(spec).contains("MALFORMED_REQUEST");
        assertThat(spec).contains("MISSING_TENANT_CONTEXT");
    }

    @Test
    void spec_documents_common_error_responses_referencing_problem_detail() throws Exception {
        String spec = fetchSpec();

        assertThat(spec).contains("\"405\":");
        assertThat(spec).contains("\"415\":");
        assertThat(spec).contains("\"500\":");
        assertThat(spec).contains("application/json");
        assertThat(spec).doesNotContain("application/problem+json");
        assertThat(spec).contains("#/components/schemas/ProblemDetail");
    }

    @Test
    void spec_marks_nullable_tenant_fields_as_nullable() throws Exception {
        String spec = fetchSpec();

        assertThat(spec).containsPattern("(?s)environmentId:\\s+type:\\s+- string\\s+- \"null\"");
        assertThat(spec).containsPattern("(?s)siteUrl:\\s+type:\\s+- string\\s+- \"null\"");
        assertThat(spec)
                .containsPattern("(?s)installerAccountId:\\s+type:\\s+- string\\s+- \"null\"");
        assertThat(spec).containsPattern("(?s)uninstalledAt:\\s+type:\\s+- string\\s+- \"null\"");
        assertThat(spec).containsPattern("(?s)purgeAfter:\\s+type:\\s+- string\\s+- \"null\"");
    }

    private String fetchSpec() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v3/api-docs.yaml"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }
}
