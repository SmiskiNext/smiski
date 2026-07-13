package io.github.smiskinext.tenant.application.mapper;

import io.github.smiskinext.tenant.application.response.TenantResponse;
import io.github.smiskinext.tenant.domain.model.Tenant;

public final class TenantResponseMapper {

    private TenantResponseMapper() {}

    public static TenantResponse toResponse(Tenant tenant, boolean created) {
        return new TenantResponse(
                tenant.getCloudId(),
                tenant.getInstallationId().value(),
                tenant.getAppId().value(),
                tenant.getStatus(),
                tenant.getEnvironmentType(),
                tenant.getInstalledAt(),
                created);
    }
}
