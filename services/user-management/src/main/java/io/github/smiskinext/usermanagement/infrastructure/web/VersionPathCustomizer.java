package io.github.smiskinext.usermanagement.infrastructure.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Rewrites springdoc-generated paths from {@code /api/1.0/...} to {@code /api/v1/...}.
 *
 * <p>Spring Framework 7's API versioning resolves {@code /{version}/...} to the bare semantic
 * version (e.g. {@code 1.0}), but Kong routes and client code use the {@code v1} prefix.
 * This customizer normalises the paths so the generated OpenAPI spec matches the gateway URLs.
 */
@Component
public class VersionPathCustomizer implements OpenApiCustomizer {

    private static final Pattern VERSION_PATTERN = Pattern.compile("/api/(\\d+\\.\\d+)/");

    @Override
    public void customise(OpenAPI openApi) {
        Paths paths = openApi.getPaths();
        if (paths == null) {
            return;
        }
        Map<String, PathItem> rewritten = new LinkedHashMap<>();
        for (var entry : paths.entrySet()) {
            String path = entry.getKey();
            var matcher = VERSION_PATTERN.matcher(path);
            if (matcher.find()) {
                String majorVersion = matcher.group(1).split("\\.")[0];
                path = matcher.replaceFirst("/api/v" + majorVersion + "/");
            }
            rewritten.put(path, entry.getValue());
        }
        paths.clear();
        paths.putAll(rewritten);
    }
}
