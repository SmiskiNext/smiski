package io.github.smiskinext.shared.infrastructure.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;

/**
 * Injects a {@code version} path parameter into every OpenAPI path that contains the {@code
 * {version}} template variable.
 *
 * <p>The versioned URL prefix {@code /api/{version}} is applied at runtime by {@link
 * ApiPathPrefixAutoConfiguration} using Spring Framework's API-versioning support. Because
 * controllers never declare a {@code @PathVariable} for {@code version}, springdoc emits the
 * template variable without a matching parameter definition. This customizer fills the gap so the
 * emitted document is self-consistent and passes the {@code path-parameters-defined} lint rule.
 *
 * <p>The parameter is added at the path-item level so it applies to all operations on that path.
 * The customizer is idempotent: it will not add a duplicate if the parameter already exists at
 * either the path level or any operation level.
 */
public class ApiVersionParameterOpenApiCustomizer implements GlobalOpenApiCustomizer {

    private static final String VERSION_TEMPLATE = "{version}";
    private static final String VERSION_PARAM_NAME = "version";

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }

        openApi.getPaths().entrySet().stream()
                .filter(entry -> entry.getKey().contains(VERSION_TEMPLATE))
                .map(Map.Entry::getValue)
                .forEach(this::ensureVersionParameter);
    }

    private void ensureVersionParameter(PathItem pathItem) {
        if (hasVersionParameter(pathItem)) {
            return;
        }

        Parameter versionParam = new Parameter()
                .in("path")
                .name(VERSION_PARAM_NAME)
                .required(true)
                .schema(new IntegerSchema())
                .example(1);

        if (pathItem.getParameters() == null) {
            pathItem.setParameters(new ArrayList<>());
        }
        pathItem.getParameters().add(versionParam);
    }

    private boolean hasVersionParameter(PathItem pathItem) {
        if (containsVersionParam(pathItem.getParameters())) {
            return true;
        }
        return pathItem.readOperations().stream()
                .anyMatch(operation -> containsVersionParam(operation.getParameters()));
    }

    private boolean containsVersionParam(List<Parameter> parameters) {
        if (parameters == null) {
            return false;
        }
        return parameters.stream()
                .anyMatch(param ->
                        VERSION_PARAM_NAME.equals(param.getName()) && "path".equals(param.getIn()));
    }
}
