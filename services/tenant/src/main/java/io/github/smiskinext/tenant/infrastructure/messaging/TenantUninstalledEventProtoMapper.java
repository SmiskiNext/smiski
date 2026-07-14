package io.github.smiskinext.tenant.infrastructure.messaging;

import com.google.protobuf.Message;

import io.github.smiskinext.event.tenant.v1.TenantUninstalled;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxEventProtoMapper;
import io.github.smiskinext.tenant.domain.event.TenantUninstalledEvent;

import org.springframework.stereotype.Component;

@Component
public class TenantUninstalledEventProtoMapper
        implements OutboxEventProtoMapper<TenantUninstalledEvent> {

    @Override
    public Class<TenantUninstalledEvent> eventType() {
        return TenantUninstalledEvent.class;
    }

    @Override
    public String dataSchema() {
        return "io.github.smiskinext.event.tenant.v1.TenantUninstalled";
    }

    @Override
    public Message toProto(TenantUninstalledEvent event) {
        TenantUninstalled.Builder builder = TenantUninstalled.newBuilder()
                .setCloudId(event.aggregateId())
                .setInstallationId(event.installationId())
                .setAppId(event.appId())
                .setUninstalledAt(event.uninstalledAt().toString())
                .setPurgeAfter(event.purgeAfter().toString())
                .setStatus(event.status().name())
                .setInstalledAt(event.installedAt().toString())
                .setUpdatedAt(event.updatedAt().toString());

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
