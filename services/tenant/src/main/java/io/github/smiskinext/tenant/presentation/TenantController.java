package io.github.smiskinext.tenant.presentation;

import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import io.github.smiskinext.shared.infrastructure.web.ProblemDetailSchema;
import io.github.smiskinext.shared.infrastructure.web.ResultResponder;
import io.github.smiskinext.tenant.application.command.RegisterTenantCommand;
import io.github.smiskinext.tenant.application.response.TenantResponse;
import io.github.smiskinext.tenant.application.usecase.RegisterTenantUseCase;
import io.github.smiskinext.tenant.domain.TenantError;
import io.github.smiskinext.tenant.presentation.request.RegisterTenantRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class TenantController {

    private final RegisterTenantUseCase registerTenantUseCase;
    private final ResultResponder responder;

    public TenantController(
            RegisterTenantUseCase registerTenantUseCase, ResultResponder responder) {
        this.registerTenantUseCase = registerTenantUseCase;
        this.responder = responder;
    }

    @Operation(
            summary = "Register a tenant",
            description =
                    "Registers a new tenant installation or acknowledges a reinstall/redelivery."
                            + " Returns 201 on first install with a Location header, 200 on"
                            + " reinstall.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Tenant registered (first install)",
                headers =
                        @Header(
                                name = "Location",
                                description = "URI of the newly created tenant resource",
                                schema = @Schema(type = "string", format = "uri")),
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = TenantResponse.class),
                                examples = @ExampleObject(name = "created", value = """
                        {
                          "tenantId": "cloud-abc-123",
                          "installationId": "install-xyz-456",
                          "appId": "app-1",
                          "status": "ACTIVE",
                          "environmentType": "PRODUCTION",
                          "installedAt": "2025-01-15T10:30:00Z"
                        }"""))),
        @ApiResponse(
                responseCode = "200",
                description = "Tenant already exists (reinstall/redelivery)",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = TenantResponse.class),
                                examples = @ExampleObject(name = "reinstall", value = """
                        {
                          "tenantId": "cloud-abc-123",
                          "installationId": "install-new-789",
                          "appId": "app-1",
                          "status": "ACTIVE",
                          "environmentType": "PRODUCTION",
                          "installedAt": "2025-01-15T10:30:00Z"
                        }"""))),
        @ApiResponse(
                responseCode = "400",
                description = "Bad Request",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = {
                                    @ExampleObject(
                                            name = "validationError",
                                            summary = "Validation failure",
                                            value = """
                            {
                              "type": "about:blank",
                              "title": "Bad Request",
                              "status": 400,
                              "detail": "The request body failed validation",
                              "code": "VALIDATION_ERROR",
                              "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a",
                              "errors": [
                                {"field": "id", "code": "REQUIRED", "message": "must not be blank"}
                              ]
                            }"""),
                                    @ExampleObject(
                                            name = "malformedRequest",
                                            summary = "Malformed JSON body",
                                            value = """
                            {
                              "type": "about:blank",
                              "title": "Bad Request",
                              "status": 400,
                              "detail": "The request body could not be read",
                              "code": "MALFORMED_REQUEST",
                              "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                            }"""),
                                    @ExampleObject(
                                            name = "missingTenantContext",
                                            summary = "Missing X-Tenant-ID header",
                                            value = """
                            {
                              "type": "about:blank",
                              "title": "Bad Request",
                              "status": 400,
                              "detail": "Tenant context is required but was not provided",
                              "code": "MISSING_TENANT_CONTEXT",
                              "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                            }""")
                                }))
    })
    @PostMapping("/tenants")
    public ResponseEntity<Object> register(@Valid @RequestBody RegisterTenantRequest request) {
        String cloudId = TenantContext.getCurrentTenant();
        RegisterTenantCommand command = request.toCommand(cloudId);
        Result<TenantResponse, TenantError> result = registerTenantUseCase.execute(command);

        return result.fold(
                response -> {
                    if (response.created()) {
                        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                                .path("/{id}")
                                .buildAndExpand(response.tenantId())
                                .toUri();
                        return ResponseEntity.created(location).body(response);
                    }
                    return ResponseEntity.ok().body(response);
                },
                error -> responder.ok(Result.<TenantResponse, TenantError>failure(error)));
    }
}
