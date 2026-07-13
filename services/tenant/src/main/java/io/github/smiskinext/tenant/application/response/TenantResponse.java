package io.github.smiskinext.tenant.application.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.smiskinext.tenant.domain.model.EnvironmentType;
import io.github.smiskinext.tenant.domain.model.TenantStatus;

import java.time.Instant;

public record TenantResponse(
        String tenantId,
        String installationId,
        String appId,
        TenantStatus status,
        EnvironmentType environmentType,
        Instant installedAt,
        @JsonIgnore boolean created) {}
