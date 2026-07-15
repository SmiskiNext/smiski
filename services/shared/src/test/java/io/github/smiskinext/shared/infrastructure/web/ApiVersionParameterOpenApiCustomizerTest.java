package io.github.smiskinext.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ApiVersionParameterOpenApiCustomizerTest {

    private final ApiVersionParameterOpenApiCustomizer customizer =
            new ApiVersionParameterOpenApiCustomizer();

    @Test
    void adds_version_path_parameter_to_versioned_path() {
        OpenAPI openApi = openApiWith("/api/{version}/tenants", "/health");

        customizer.customise(openApi);

        var params = openApi.getPaths().get("/api/{version}/tenants").getParameters();
        assertThat(params).hasSize(1);

        Parameter versionParam = params.getFirst();
        assertThat(versionParam.getName()).isEqualTo("version");
        assertThat(versionParam.getIn()).isEqualTo("path");
        assertThat(versionParam.getRequired()).isTrue();
        assertThat(versionParam.getSchema().getType()).isEqualTo("integer");
    }

    @Test
    void does_not_add_version_parameter_to_non_versioned_path() {
        OpenAPI openApi = openApiWith("/api/{version}/tenants", "/health");

        customizer.customise(openApi);

        var healthPath = openApi.getPaths().get("/health");
        assertThat(healthPath.getParameters()).isNull();
    }

    @Test
    void does_not_duplicate_parameter_when_run_twice() {
        OpenAPI openApi = openApiWith("/api/{version}/tenants");

        customizer.customise(openApi);
        customizer.customise(openApi);

        var params = openApi.getPaths().get("/api/{version}/tenants").getParameters();
        assertThat(params).hasSize(1);
    }

    @Test
    void does_not_duplicate_when_operation_already_declares_version() {
        OpenAPI openApi = openApiWith("/api/{version}/meetings");
        Parameter existing = new Parameter().in("path").name("version").required(true);
        openApi.getPaths().get("/api/{version}/meetings").getGet().setParameters(new ArrayList<>());
        openApi.getPaths()
                .get("/api/{version}/meetings")
                .getGet()
                .getParameters()
                .add(existing);

        customizer.customise(openApi);

        assertThat(openApi.getPaths().get("/api/{version}/meetings").getParameters())
                .isNull();
    }

    @Test
    void handles_null_paths_gracefully() {
        OpenAPI openApi = new OpenAPI();

        customizer.customise(openApi);

        assertThat(openApi.getPaths()).isNull();
    }

    private OpenAPI openApiWith(String... pathKeys) {
        Paths paths = new Paths();
        for (String key : pathKeys) {
            Operation operation = new Operation().responses(new ApiResponses());
            PathItem pathItem = new PathItem().get(operation);
            paths.addPathItem(key, pathItem);
        }
        return new OpenAPI().paths(paths);
    }
}
