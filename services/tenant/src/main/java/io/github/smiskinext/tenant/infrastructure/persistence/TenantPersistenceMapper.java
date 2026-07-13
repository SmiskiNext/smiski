package io.github.smiskinext.tenant.infrastructure.persistence;

import io.github.smiskinext.tenant.domain.model.EnvironmentType;
import io.github.smiskinext.tenant.domain.model.Tenant;
import io.github.smiskinext.tenant.domain.model.TenantStatus;
import io.github.smiskinext.tenant.domain.model.valueobject.AppId;
import io.github.smiskinext.tenant.domain.model.valueobject.InstallationId;

final class TenantPersistenceMapper {

    private TenantPersistenceMapper() {}

    static Tenant toDomain(TenantJpaEntity entity) {
        return Tenant.reconstitute(
                entity.getTenantId(),
                new InstallationId(entity.getInstallationId()),
                new AppId(entity.getAppId()),
                entity.getAppVersion(),
                entity.getEnvironmentId(),
                EnvironmentType.valueOf(entity.getEnvironmentType()),
                entity.getSiteUrl(),
                entity.getInstallerAccountId(),
                TenantStatus.valueOf(entity.getStatus()),
                entity.getInstalledAt(),
                entity.getUpdatedAt(),
                entity.getUninstalledAt(),
                entity.getPurgeAfter());
    }

    static TenantJpaEntity toEntity(Tenant tenant) {
        return new TenantJpaEntity(
                tenant.getCloudId(),
                tenant.getInstallationId().value(),
                tenant.getAppId().value(),
                tenant.getEnvironmentType().name(),
                tenant.getEnvironmentId(),
                tenant.getSiteUrl(),
                tenant.getInstallerAccountId(),
                tenant.getAppVersion(),
                tenant.getStatus().name(),
                tenant.getInstalledAt(),
                tenant.getUpdatedAt(),
                tenant.getUninstalledAt(),
                tenant.getPurgeAfter());
    }

    static void updateEntity(TenantJpaEntity entity, Tenant tenant) {
        entity.setInstallationId(tenant.getInstallationId().value());
        entity.setAppId(tenant.getAppId().value());
        entity.setAppVersion(tenant.getAppVersion());
        entity.setEnvironmentType(tenant.getEnvironmentType().name());
        entity.setEnvironmentId(tenant.getEnvironmentId());
        entity.setSiteUrl(tenant.getSiteUrl());
        entity.setInstallerAccountId(tenant.getInstallerAccountId());
        entity.setStatus(tenant.getStatus().name());
        entity.setUpdatedAt(tenant.getUpdatedAt());
        entity.setUninstalledAt(tenant.getUninstalledAt());
        entity.setPurgeAfter(tenant.getPurgeAfter());
    }
}
