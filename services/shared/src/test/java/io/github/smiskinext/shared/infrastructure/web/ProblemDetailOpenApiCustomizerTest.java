package io.github.smiskinext.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

class ProblemDetailOpenApiCustomizerTest {

    private final ProblemDetailOpenApiCustomizer customizer = new ProblemDetailOpenApiCustomizer();

    @Test
    void registers_problem_detail_schema_in_components() {
        OpenAPI openApi = minimalOpenApi();

        customizer.customise(openApi);

        assertThat(openApi.getComponents().getSchemas()).containsKey("ProblemDetail");
        assertThat(openApi.getComponents().getSchemas()).containsKey("Violation");
    }

    @Test
    void appends_common_error_responses_to_operations() {
        OpenAPI openApi = minimalOpenApi();

        customizer.customise(openApi);

        var responses = openApi.getPaths().get("/test").getGet().getResponses();
        assertThat(responses).containsKeys("405", "415", "500");
        for (String status : new String[] {"405", "415", "500"}) {
            var content = responses.get(status).getContent();
            assertThat(content).containsKey("application/problem+json");
            var schema = content.get("application/problem+json").getSchema();
            assertThat(schema.get$ref()).isEqualTo("#/components/schemas/ProblemDetail");
        }
    }

    @Test
    void does_not_overwrite_existing_status() {
        OpenAPI openApi = minimalOpenApi();
        var existingResponse =
                new io.swagger.v3.oas.models.responses.ApiResponse().description("Custom 500");
        openApi.getPaths()
                .get("/test")
                .getGet()
                .getResponses()
                .addApiResponse("500", existingResponse);

        customizer.customise(openApi);

        var responses = openApi.getPaths().get("/test").getGet().getResponses();
        assertThat(responses.get("500").getDescription()).isEqualTo("Custom 500");
        assertThat(responses.get("500").getContent()).isNull();
    }

    private OpenAPI minimalOpenApi() {
        Operation operation = new Operation().responses(new ApiResponses());
        PathItem pathItem = new PathItem().get(operation);
        Paths paths = new Paths();
        paths.addPathItem("/test", pathItem);
        return new OpenAPI().components(new Components()).paths(paths);
    }
}
