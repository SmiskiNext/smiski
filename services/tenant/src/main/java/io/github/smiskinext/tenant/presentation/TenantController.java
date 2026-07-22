package io.github.smiskinext.tenant.presentation;

import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import io.github.smiskinext.shared.infrastructure.web.ProblemDetailSchema;
import io.github.smiskinext.shared.infrastructure.web.ResultResponder;
import io.github.smiskinext.tenant.application.command.RegisterTenantCommand;
import io.github.smiskinext.tenant.application.command.UninstallTenantCommand;
import io.github.smiskinext.tenant.application.result.RegisterTenantResult;
import io.github.smiskinext.tenant.application.result.UninstallTenantResult;
import io.github.smiskinext.tenant.application.usecase.RegisterTenantUseCase;
import io.github.smiskinext.tenant.application.usecase.UninstallTenantUseCase;
import io.github.smiskinext.tenant.domain.TenantError;
import io.github.smiskinext.tenant.presentation.request.RegisterTenantRequest;
import io.github.smiskinext.tenant.presentation.request.UninstallTenantRequest;
import io.github.smiskinext.tenant.presentation.response.TenantResponse;
import io.github.smiskinext.tenant.presentation.response.UninstallTenantResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@Tag(
        name = "tenant-controller",
        description = "Tenant lifecycle: register (install) and uninstall Forge app tenants")
public class TenantController {

    private final RegisterTenantUseCase registerTenantUseCase;
    private final UninstallTenantUseCase uninstallTenantUseCase;
    private final ResultResponder responder;

    public TenantController(
            RegisterTenantUseCase registerTenantUseCase,
            UninstallTenantUseCase uninstallTenantUseCase,
            ResultResponder responder) {
        this.registerTenantUseCase = registerTenantUseCase;
        this.uninstallTenantUseCase = uninstallTenantUseCase;
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
                          "installationId": "install-xyz-456",
                          "appId": "app-1",
                          "appVersion": "1.0.0",
                          "environmentId": "env-1",
                          "siteUrl": "https://example.atlassian.net",
                          "installerAccountId": "installer-1",
                          "status": "ACTIVE",
                          "installedAt": "2025-01-15T10:30:00Z",
                          "updatedAt": "2025-01-15T10:30:00Z",
                          "uninstalledAt": null,
                          "purgeAfter": null
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
                          "installationId": "install-new-789",
                          "appId": "app-1",
                          "appVersion": "2.0.0",
                          "environmentId": null,
                          "siteUrl": null,
                          "installerAccountId": null,
                          "status": "ACTIVE",
                          "installedAt": "2025-01-15T10:30:00Z",
                          "updatedAt": "2025-06-15T10:30:00Z",
                          "uninstalledAt": null,
                          "purgeAfter": null
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
        Result<RegisterTenantResult, TenantError> result = registerTenantUseCase.execute(command);

        return result.fold(
                resultValue -> {
                    if (resultValue.created()) {
                        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                                .path("/{id}")
                                .buildAndExpand(resultValue.tenantId())
                                .toUri();
                        return ResponseEntity.created(location)
                                .body(TenantResponse.from(resultValue));
                    }
                    return ResponseEntity.ok().body(TenantResponse.from(resultValue));
                },
                error -> responder.ok(Result.<RegisterTenantResult, TenantError>failure(error)));
    }

    @Operation(
            summary = "Uninstall a tenant",
            description =
                    "Records a Forge app uninstall. Marks the tenant as UNINSTALLED and schedules"
                            + " purge. Idempotent for already-uninstalled tenants.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Tenant uninstalled (or already uninstalled)",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = UninstallTenantResponse.class),
                                examples = @ExampleObject(name = "uninstalled", value = """
                        {
                          "installationId": "install-xyz-456",
                          "appId": "app-1",
                          "appVersion": "1.0.0",
                          "environmentId": null,
                          "siteUrl": null,
                          "installerAccountId": null,
                          "status": "UNINSTALLED",
                          "installedAt": "2025-01-15T10:30:00Z",
                          "updatedAt": "2025-06-15T10:30:00Z",
                          "uninstalledAt": "2025-06-15T10:30:00Z",
                          "purgeAfter": "2025-07-15T10:30:00Z"
                        }"""))),
        @ApiResponse(
                responseCode = "400",
                description = "Missing tenant context",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples =
                                        @ExampleObject(
                                                name = "missingTenantContext",
                                                value = """
                        {
                          "type": "about:blank",
                          "title": "Bad Request",
                          "status": 400,
                          "detail": "Tenant context is required but was not provided",
                          "code": "MISSING_TENANT_CONTEXT",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }"""))),
        @ApiResponse(
                responseCode = "404",
                description = "Tenant not found",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetailSchema.class),
                                examples = @ExampleObject(name = "notFound", value = """
                        {
                          "type": "about:blank",
                          "title": "Tenant Not Found",
                          "status": 404,
                          "detail": "No tenant exists for the given cloud ID",
                          "code": "TENANT_NOT_FOUND",
                          "traceId": "6d3e5f1a2b4c7d8e9f0a1b2c3d4e5f6a"
                        }""")))
    })
    @DeleteMapping("/tenants")
    public ResponseEntity<Object> uninstall(
            @RequestBody(required = false) UninstallTenantRequest request) {
        String cloudId = TenantContext.getCurrentTenant();
        UninstallTenantCommand command = new UninstallTenantCommand(cloudId);
        Result<UninstallTenantResult, TenantError> result = uninstallTenantUseCase.execute(command);

        return result.fold(
                resultValue -> ResponseEntity.ok().body(UninstallTenantResponse.from(resultValue)),
                error -> responder.ok(Result.<UninstallTenantResult, TenantError>failure(error)));
    }
}
