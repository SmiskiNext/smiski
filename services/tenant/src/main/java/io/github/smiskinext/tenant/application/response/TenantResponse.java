package io.github.smiskinext.tenant.application.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.smiskinext.tenant.domain.model.EnvironmentType;
import io.github.smiskinext.tenant.domain.model.TenantStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Tenant registration result")
public record TenantResponse(
        @Schema(description = "Unique tenant identifier (cloud ID)", example = "cloud-abc-123")
        String tenantId,

        @Schema(description = "Installation identifier", example = "install-xyz-456")
        String installationId,

        @Schema(description = "Application identifier", example = "app-1")
        String appId,

        @Schema(description = "Current tenant status", example = "ACTIVE")
        TenantStatus status,

        @Schema(description = "Deployment environment type", example = "PRODUCTION")
        EnvironmentType environmentType,

        @Schema(description = "Timestamp of initial installation", example = "2025-01-15T10:30:00Z")
        Instant installedAt,

        @JsonIgnore boolean created) {}
