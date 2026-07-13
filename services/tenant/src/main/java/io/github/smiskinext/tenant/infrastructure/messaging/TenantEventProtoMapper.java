package io.github.smiskinext.tenant.infrastructure.messaging;

import io.github.smiskinext.event.tenant.v1.TenantInstalled;
import io.github.smiskinext.tenant.domain.event.TenantInstalledEvent;

public final class TenantEventProtoMapper {

    private TenantEventProtoMapper() {}

    public static TenantInstalled toProto(TenantInstalledEvent event) {
        TenantInstalled.Builder builder = TenantInstalled.newBuilder()
                .setCloudId(event.aggregateId())
                .setInstallationId(event.installationId())
                .setAppId(event.appId())
                .setEnvironmentType(event.environmentType())
                .setInstalledAt(event.installedAt().toString());

        if (event.appVersion() != null) {
            builder.setAppVersion(event.appVersion());
        }
        if (event.environmentId() != null) {
            builder.setEnvironmentId(event.environmentId());
        }
        if (event.siteUrl() != null) {
            builder.setSiteUrl(event.siteUrl());
        }
        if (event.installerAccountId() != null) {
            builder.setInstallerAccountId(event.installerAccountId());
        }
        return builder.build();
    }
}
