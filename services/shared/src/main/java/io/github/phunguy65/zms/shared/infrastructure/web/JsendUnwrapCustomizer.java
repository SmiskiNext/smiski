package io.github.phunguy65.zms.shared.infrastructure.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Strips {@link JsendResponse} envelope schemas from the generated OpenAPI spec.
 *
 * <p>Controllers return {@code ResponseEntity<JsendResponse<T>>}, so springdoc generates wrapper
 * schemas like {@code JsendResponseLoginResponse} with {@code {status, data, message}} fields.
 * Client-side interceptors ({@code JsendUnwrapInterceptor}) already strip the envelope at runtime,
 * so generated client types should reference the inner payload type directly.
 *
 * <p>For each component schema whose name starts with {@code JsendResponse}, this customizer
 * inspects the {@code data} property and rewrites response schemas in one of three ways:
 *
 * <ol>
 *   <li><b>Named-type payload</b> — {@code data} is a direct {@code $ref} (e.g.
 *       {@code JsendResponse<MeetingResponse>}). The response schema is replaced with that
 *       {@code $ref}.</li>
 *   <li><b>Inline payload</b> — {@code data} is a non-{@code $ref} schema such as
 *       {@code type: array} with inline {@code items} (e.g. {@code JsendResponse<List<T>>}),
 *       an inline object, or a primitive. The response schema is replaced with the full inline
 *       {@code data} schema, preserving {@code type}, {@code items}, {@code properties}, etc.</li>
 *   <li><b>Empty payload</b> — {@code data} is absent or has no type information (e.g.
 *       {@code JsendResponse<Void>}). The response content is cleared entirely.</li>
 * </ol>
 *
 * <p>After rewriting all responses, every {@code JsendResponse*} wrapper is removed from
 * {@code components/schemas}.
 *
 * <p>Placed in the shared module and auto-discovered by all services via
 * {@code scanBasePackages = "io.github.phunguy65.zms.shared"}.
 */
@Component
public class JsendUnwrapCustomizer implements OpenApiCustomizer {

    private static final String JSEND_SCHEMA_PREFIX = "JsendResponse";
    private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";

    @Override
    public void customise(OpenAPI openApi) {
        Components components = openApi.getComponents();
        if (components == null || components.getSchemas() == null) {
            return;
        }

        Map<String, Schema> schemas = components.getSchemas();

        Map<String, Schema> wrapperToInnerSchema = buildWrapperMapping(schemas);
        if (wrapperToInnerSchema.isEmpty()) {
            return;
        }

        if (openApi.getPaths() != null) {
            for (PathItem pathItem : openApi.getPaths().values()) {
                rewritePathItem(pathItem, wrapperToInnerSchema);
            }
        }

        for (String wrapperName : wrapperToInnerSchema.keySet()) {
            schemas.remove(wrapperName);
        }
    }

    /**
     * Scans component schemas for {@code JsendResponse*} wrappers and maps each wrapper name to its
     * inner {@code data} property schema. The mapped value is {@code null} when the wrapper has no
     * meaningful payload (e.g. {@code JsendResponse<Void>}); otherwise it is the full {@link Schema}
     * object describing the payload (named ref, array, inline object, or primitive).
     */
    private Map<String, Schema> buildWrapperMapping(Map<String, Schema> schemas) {
        Map<String, Schema> mapping = new HashMap<>();

        for (Map.Entry<String, Schema> entry : schemas.entrySet()) {
            String name = entry.getKey();
            if (!name.startsWith(JSEND_SCHEMA_PREFIX)) {
                continue;
            }

            Schema wrapperSchema = entry.getValue();
            Map<String, Schema> properties = wrapperSchema.getProperties();
            if (properties == null) {
                mapping.put(name, null);
                continue;
            }

            Schema dataProperty = properties.get("data");
            mapping.put(name, hasPayload(dataProperty) ? dataProperty : null);
        }

        return mapping;
    }

    /**
     * Returns {@code true} when the {@code data} property carries enough type information to be
     * treated as a real payload schema.
     */
    private boolean hasPayload(Schema dataProperty) {
        if (dataProperty == null) {
            return false;
        }
        return dataProperty.get$ref() != null
                || dataProperty.getType() != null
                || dataProperty.getProperties() != null
                || dataProperty.getItems() != null
                || dataProperty.get$schema() != null;
    }

    /** Rewrites response schemas across all HTTP operations in a {@link PathItem}. */
    private void rewritePathItem(PathItem pathItem, Map<String, Schema> wrapperToInnerSchema) {
        for (Operation operation : allOperations(pathItem)) {
            rewriteOperation(operation, wrapperToInnerSchema);
        }
    }

    /** Rewrites response schema references for a single {@link Operation}. */
    private void rewriteOperation(Operation operation, Map<String, Schema> wrapperToInnerSchema) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            return;
        }

        for (ApiResponse response : responses.values()) {
            Content content = response.getContent();
            if (content == null) {
                continue;
            }

            for (Map.Entry<String, MediaType> mediaEntry : content.entrySet()) {
                MediaType mediaType = mediaEntry.getValue();
                Schema schema = mediaType.getSchema();
                if (schema == null || schema.get$ref() == null) {
                    continue;
                }

                String schemaName = extractSchemaName(schema.get$ref());
                if (schemaName == null || !wrapperToInnerSchema.containsKey(schemaName)) {
                    continue;
                }

                Schema innerSchema = wrapperToInnerSchema.get(schemaName);
                if (innerSchema != null) {
                    mediaType.setSchema(innerSchema);
                } else {
                    response.setContent(null);
                    break;
                }
            }
        }
    }

    /**
     * Extracts the schema name from a {@code $ref} string.
     *
     * @param ref e.g. {@code "#/components/schemas/JsendResponseLoginResponse"}
     * @return e.g. {@code "JsendResponseLoginResponse"}, or {@code null} if not a schema ref
     */
    private String extractSchemaName(String ref) {
        if (ref != null && ref.startsWith(SCHEMA_REF_PREFIX)) {
            return ref.substring(SCHEMA_REF_PREFIX.length());
        }
        return null;
    }

    /** Collects all non-null {@link Operation}s from a {@link PathItem}. */
    private List<Operation> allOperations(PathItem pathItem) {
        return Stream.of(
                        pathItem.getGet(),
                        pathItem.getPost(),
                        pathItem.getPut(),
                        pathItem.getPatch(),
                        pathItem.getDelete(),
                        pathItem.getHead(),
                        pathItem.getOptions(),
                        pathItem.getTrace())
                .filter(op -> op != null)
                .toList();
    }
}
