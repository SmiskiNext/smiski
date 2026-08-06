package io.github.smiskinext.shared.infrastructure.web;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Schema-only type that drives springdoc generation of the reusable {@code ProblemDetail} OpenAPI
 * component. This class is never instantiated at runtime; it exists solely to declare the RFC 9457
 * Problem Details body shape — including the extension members produced by {@link
 * ProblemDetailMapper} — so the generated spec documents the error contract accurately.
 */
@Schema(name = "ProblemDetail", description = "RFC 9457 Problem Details response body")
public record ProblemDetailSchema(
        @Schema(description = "URI reference identifying the problem type", example = "about:blank")
        String type,

        @Schema(
                description = "Short human-readable summary of the problem",
                example = "Bad Request")
        String title,

        @Schema(description = "HTTP status code", example = "400")
        int status,

        @Schema(
                description = "Human-readable explanation specific to this occurrence",
                example = "The request body failed validation")
        String detail,

        @Schema(description = "Machine-readable error code", example = "VALIDATION_ERROR")
        String code,

        @Schema(
                description = "Distributed trace identifier for correlation",
                example = "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a")
        String traceId,

        @ArraySchema(
                schema = @Schema(implementation = ViolationSchema.class),
                arraySchema =
                        @Schema(
                                description =
                                        "Field-level validation errors (present when code is VALIDATION_ERROR)"))
        @Nullable List<ViolationSchema> errors) {

    /**
     * Schema-only type representing a single field-level validation failure within the {@code
     * errors} array.
     */
    @Schema(name = "Violation", description = "Field-level validation error")
    public record ViolationSchema(
            @Schema(description = "Request field that failed validation", example = "app.id")
            String field,

            @Schema(
                    description = "Machine-readable violation category",
                    allowableValues = {
                        "REQUIRED",
                        "INVALID_FORMAT",
                        "TOO_SHORT",
                        "TOO_LONG",
                        "INVALID_VALUE"
                    },
                    example = "REQUIRED")
            String code,

            @Schema(
                    description = "Server-localized human-readable message",
                    nullable = true,
                    example = "must not be blank")
            @Nullable String message) {}
}
