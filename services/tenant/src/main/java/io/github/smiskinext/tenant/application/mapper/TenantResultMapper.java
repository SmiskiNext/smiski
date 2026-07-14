package io.github.smiskinext.tenant.application.mapper;

import io.github.smiskinext.tenant.application.result.RegisterTenantResult;
import io.github.smiskinext.tenant.domain.model.Tenant;

public final class TenantResultMapper {

    private TenantResultMapper() {}

    public static RegisterTenantResult toResult(Tenant tenant, boolean created) {
        return new RegisterTenantResult(
                tenant.getCloudId(),
                tenant.getInstallationId().value(),
                tenant.getAppId().value(),
                tenant.getStatus(),
                tenant.getInstalledAt(),
                created);
    }
}
