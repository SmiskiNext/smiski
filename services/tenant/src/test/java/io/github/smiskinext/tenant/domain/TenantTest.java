package io.github.smiskinext.tenant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.shared.domain.DomainEvent;
import io.github.smiskinext.tenant.domain.event.TenantInstalledEvent;
import io.github.smiskinext.tenant.domain.model.EnvironmentType;
import io.github.smiskinext.tenant.domain.model.Tenant;
import io.github.smiskinext.tenant.domain.model.TenantStatus;
import io.github.smiskinext.tenant.domain.model.valueobject.AppId;
import io.github.smiskinext.tenant.domain.model.valueobject.InstallationId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TenantTest {

    @Test
    void install_creates_active_tenant_and_registers_event() {
        Tenant tenant = Tenant.install(
                "cloud-123",
                new InstallationId("install-1"),
                new AppId("app-1"),
                "1.0.0",
                "env-1",
                EnvironmentType.PRODUCTION,
                "https://example.atlassian.net",
                "installer-1");

        assertThat(tenant.getCloudId()).isEqualTo("cloud-123");
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.getInstallationId().value()).isEqualTo("install-1");
        assertThat(tenant.getInstalledAt()).isNotNull();

        List<DomainEvent> events = tenant.getDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(TenantInstalledEvent.class);

        TenantInstalledEvent event = (TenantInstalledEvent) events.getFirst();
        assertThat(event.aggregateId()).isEqualTo("cloud-123");
        assertThat(event.installationId()).isEqualTo("install-1");
        assertThat(event.appId()).isEqualTo("app-1");
    }

    @Test
    void reinstall_replaces_installation_reactivates_and_preserves_installed_at() {
        Tenant tenant = Tenant.install(
                "cloud-123",
                new InstallationId("install-1"),
                new AppId("app-1"),
                "1.0.0",
                null,
                EnvironmentType.PRODUCTION,
                null,
                null);
        Instant originalInstalledAt = tenant.getInstalledAt();
        Instant originalUpdatedAt = tenant.getUpdatedAt();
        tenant.clearDomainEvents();

        Tenant reconstituted = Tenant.reconstitute(
                tenant.getCloudId(),
                tenant.getInstallationId(),
                tenant.getAppId(),
                tenant.getAppVersion(),
                null,
                tenant.getEnvironmentType(),
                null,
                null,
                TenantStatus.UNINSTALLED,
                originalInstalledAt,
                originalUpdatedAt,
                Instant.now(),
                null);

        reconstituted.reinstall(
                new InstallationId("install-2"),
                new AppId("app-2"),
                "2.0.0",
                "env-2",
                EnvironmentType.STAGING,
                "https://new.atlassian.net",
                "installer-2");

        assertThat(reconstituted.getInstallationId().value()).isEqualTo("install-2");
        assertThat(reconstituted.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(reconstituted.getInstalledAt()).isEqualTo(originalInstalledAt);
        assertThat(reconstituted.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);

        List<DomainEvent> events = reconstituted.getDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(TenantInstalledEvent.class);
    }
}
