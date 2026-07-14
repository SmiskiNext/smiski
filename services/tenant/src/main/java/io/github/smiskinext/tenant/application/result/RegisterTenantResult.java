package io.github.smiskinext.tenant.application.result;

import io.github.smiskinext.tenant.domain.model.TenantStatus;

import java.time.Instant;

public record RegisterTenantResult(
        String tenantId,
        String installationId,
        String appId,
        TenantStatus status,
        Instant installedAt,
        boolean created) {}
