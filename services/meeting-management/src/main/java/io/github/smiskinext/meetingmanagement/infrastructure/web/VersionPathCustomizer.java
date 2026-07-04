package io.github.smiskinext.meetingmanagement.infrastructure.web;

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
 * @see io.github.phunguy65.zms.usermanagement.infrastructure.web.VersionPathCustomizer
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
