package io.github.smiskinext.tenant.application.mapper;

import io.github.smiskinext.tenant.application.result.RegisterTenantResult;
import io.github.smiskinext.tenant.application.result.UninstallTenantResult;
import io.github.smiskinext.tenant.domain.model.Tenant;

public final class TenantResultMapper {

    private TenantResultMapper() {}

    public static RegisterTenantResult toResult(Tenant tenant, boolean created) {
        return new RegisterTenantResult(
                tenant.getCloudId(),
                tenant.getInstallationId().value(),
                tenant.getAppId().value(),
                tenant.getAppVersion(),
                tenant.getEnvironmentId(),
                tenant.getSiteUrl(),
                tenant.getInstallerAccountId(),
                tenant.getStatus(),
                tenant.getInstalledAt(),
                tenant.getUpdatedAt(),
                tenant.getUninstalledAt(),
                tenant.getPurgeAfter(),
                created);
    }

    public static UninstallTenantResult toUninstallResult(Tenant tenant) {
        return new UninstallTenantResult(
                tenant.getCloudId(),
                tenant.getInstallationId().value(),
                tenant.getAppId().value(),
                tenant.getAppVersion(),
                tenant.getEnvironmentId(),
                tenant.getSiteUrl(),
                tenant.getInstallerAccountId(),
                tenant.getStatus(),
                tenant.getInstalledAt(),
                tenant.getUpdatedAt(),
                tenant.getUninstalledAt(),
                tenant.getPurgeAfter());
    }
}
