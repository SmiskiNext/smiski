package io.github.smiskinext.shared.infrastructure.web;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;

/**
 * Registers the shared {@code ProblemDetail} schema into every service's OpenAPI {@code
 * components.schemas} and appends common error responses ({@code 405}, {@code 415}, {@code 500}) to
 * every operation that does not already declare them.
 *
 * <p>These responses correspond to the exceptions handled uniformly by {@link
 * GlobalExceptionHandler} across all services. By attaching them here once, every future controller
 * inherits the documentation without per-endpoint boilerplate.
 */
public class ProblemDetailOpenApiCustomizer implements GlobalOpenApiCustomizer {

    private static final String PROBLEM_DETAIL_SCHEMA_NAME = "ProblemDetail";
    private static final String VIOLATION_SCHEMA_NAME = "Violation";
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final List<String> COMMON_ERROR_STATUSES = List.of("405", "415", "500");

    @Override
    public void customise(OpenAPI openApi) {
        registerSchemas(openApi);
        appendCommonResponses(openApi);
    }

    private void registerSchemas(OpenAPI openApi) {
        var resolved = ModelConverters.getInstance().readAll(ProblemDetailSchema.class);
        resolved.forEach((name, schema) -> {
            if (openApi.getComponents().getSchemas() == null
                    || !openApi.getComponents().getSchemas().containsKey(name)) {
                openApi.getComponents().addSchemas(name, schema);
            }
        });
    }

    @SuppressWarnings("rawtypes")
    private void appendCommonResponses(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }

        Schema<?> ref = new Schema<>().$ref("#/components/schemas/" + PROBLEM_DETAIL_SCHEMA_NAME);

        Map<String, String> descriptions = Map.of(
                "405", "Method Not Allowed",
                "415", "Unsupported Media Type",
                "500", "Internal Server Error");

        openApi.getPaths().values().stream()
                .flatMap(pathItem -> pathItem.readOperations().stream())
                .forEach(operation -> {
                    var responses = operation.getResponses();
                    for (String status : COMMON_ERROR_STATUSES) {
                        if (responses.containsKey(status)) {
                            continue;
                        }
                        Content content = new Content()
                                .addMediaType(PROBLEM_JSON, new MediaType().schema(ref));
                        ApiResponse response = new ApiResponse()
                                .description(descriptions.get(status))
                                .content(content);
                        responses.addApiResponse(status, response);
                    }
                });
    }
}
