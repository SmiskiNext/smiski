package io.github.phunguy65.zms.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsendUnwrapCustomizerTest {

    private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";
    private static final String APPLICATION_JSON = "application/json";

    private final JsendUnwrapCustomizer customizer = new JsendUnwrapCustomizer();

    @Test
    void rewritesNamedRefWrapperToInnerRef() {
        OpenAPI openApi = new OpenAPI().components(new Components()).paths(new Paths());

        Schema<?> fooSchema = new ObjectSchema().addProperty("name", new Schema<>().type("string"));
        openApi.getComponents().addSchemas("Foo", fooSchema);

        Schema<?> wrapper = buildWrapper(refSchema("Foo"));
        openApi.getComponents().addSchemas("JsendResponseFoo", wrapper);

        openApi.getPaths()
                .addPathItem(
                        "/foo",
                        new PathItem().get(operationReturning(refSchema("JsendResponseFoo"))));

        customizer.customise(openApi);

        Schema<?> responseSchema = extractResponseSchema(openApi, "/foo");
        assertThat(responseSchema.get$ref()).isEqualTo(SCHEMA_REF_PREFIX + "Foo");
        assertThat(openApi.getComponents().getSchemas()).doesNotContainKey("JsendResponseFoo");
        assertThat(openApi.getComponents().getSchemas()).containsKey("Foo");
    }

    @Test
    void rewritesArrayWrapperToInlineArraySchema() {
        OpenAPI openApi = new OpenAPI().components(new Components()).paths(new Paths());

        Schema<?> itemSchema = new ObjectSchema().addProperty("id", new Schema<>().type("string"));
        openApi.getComponents().addSchemas("Item", itemSchema);

        Schema<?> dataArray = new ArraySchema().items(refSchema("Item"));
        Schema<?> wrapper = buildWrapper(dataArray);
        openApi.getComponents().addSchemas("JsendResponseListItem", wrapper);

        openApi.getPaths()
                .addPathItem(
                        "/items",
                        new PathItem().get(operationReturning(refSchema("JsendResponseListItem"))));

        customizer.customise(openApi);

        Schema<?> responseSchema = extractResponseSchema(openApi, "/items");
        assertThat(responseSchema.get$ref()).isNull();
        assertThat(responseSchema.getType()).isEqualTo("array");
        assertThat(responseSchema.getItems()).isNotNull();
        assertThat(responseSchema.getItems().get$ref()).isEqualTo(SCHEMA_REF_PREFIX + "Item");
        assertThat(openApi.getComponents().getSchemas()).doesNotContainKey("JsendResponseListItem");
    }

    @Test
    void clearsContentForVoidWrapper() {
        OpenAPI openApi = new OpenAPI().components(new Components()).paths(new Paths());

        Schema<?> wrapper = buildWrapper(null);
        openApi.getComponents().addSchemas("JsendResponseVoid", wrapper);

        PathItem pathItem =
                new PathItem().delete(operationReturning(refSchema("JsendResponseVoid")));
        openApi.getPaths().addPathItem("/delete", pathItem);

        customizer.customise(openApi);

        ApiResponse response =
                openApi.getPaths().get("/delete").getDelete().getResponses().get("200");
        assertThat(response.getContent()).isNull();
        assertThat(openApi.getComponents().getSchemas()).doesNotContainKey("JsendResponseVoid");
    }

    @Test
    void noOpWhenNoJsendWrappersPresent() {
        OpenAPI openApi = new OpenAPI().components(new Components()).paths(new Paths());

        Schema<?> fooSchema = new ObjectSchema();
        openApi.getComponents().addSchemas("Foo", fooSchema);

        openApi.getPaths()
                .addPathItem("/foo", new PathItem().get(operationReturning(refSchema("Foo"))));

        customizer.customise(openApi);

        Schema<?> responseSchema = extractResponseSchema(openApi, "/foo");
        assertThat(responseSchema.get$ref()).isEqualTo(SCHEMA_REF_PREFIX + "Foo");
        assertThat(openApi.getComponents().getSchemas()).containsOnlyKeys("Foo");
    }

    private static Schema<?> refSchema(String name) {
        Schema<?> schema = new Schema<>();
        schema.set$ref(SCHEMA_REF_PREFIX + name);
        return schema;
    }

    private static Schema<?> buildWrapper(Schema<?> dataProperty) {
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("status", new Schema<>().type("string"));
        if (dataProperty != null) {
            properties.put("data", dataProperty);
        }
        properties.put("message", new Schema<>().type("string"));

        Schema<?> wrapper = new ObjectSchema();
        wrapper.setProperties(properties);
        return wrapper;
    }

    private static Operation operationReturning(Schema<?> schema) {
        MediaType mediaType = new MediaType().schema(schema);
        Content content = new Content().addMediaType(APPLICATION_JSON, mediaType);
        ApiResponse response = new ApiResponse().description("OK").content(content);
        ApiResponses responses = new ApiResponses().addApiResponse("200", response);
        return new Operation().responses(responses);
    }

    private static Schema<?> extractResponseSchema(OpenAPI openApi, String path) {
        return openApi.getPaths()
                .get(path)
                .getGet()
                .getResponses()
                .get("200")
                .getContent()
                .get(APPLICATION_JSON)
                .getSchema();
    }
}
