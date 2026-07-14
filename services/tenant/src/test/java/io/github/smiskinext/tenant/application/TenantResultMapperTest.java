package io.github.smiskinext.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.tenant.application.mapper.TenantResultMapper;
import io.github.smiskinext.tenant.application.result.RegisterTenantResult;
import io.github.smiskinext.tenant.application.result.UninstallTenantResult;
import io.github.smiskinext.tenant.domain.model.Tenant;
import io.github.smiskinext.tenant.domain.model.TenantStatus;
import io.github.smiskinext.tenant.domain.model.valueobject.AppId;
import io.github.smiskinext.tenant.domain.model.valueobject.InstallationId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TenantResultMapperTest {

    @Test
    void maps_domain_to_register_result() {
        Instant now = Instant.now();
        Tenant tenant = Tenant.reconstitute(
                "cloud-1",
                new InstallationId("install-1"),
                new AppId("app-1"),
                "1.0.0",
                "env-1",
                "https://example.atlassian.net",
                "installer-1",
                TenantStatus.ACTIVE,
                now,
                now,
                null,
                null);

        RegisterTenantResult result = TenantResultMapper.toResult(tenant, true);

        assertThat(result.tenantId()).isEqualTo("cloud-1");
        assertThat(result.installationId()).isEqualTo("install-1");
        assertThat(result.appId()).isEqualTo("app-1");
        assertThat(result.appVersion()).isEqualTo("1.0.0");
        assertThat(result.environmentId()).isEqualTo("env-1");
        assertThat(result.siteUrl()).isEqualTo("https://example.atlassian.net");
        assertThat(result.installerAccountId()).isEqualTo("installer-1");
        assertThat(result.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(result.installedAt()).isEqualTo(now);
        assertThat(result.updatedAt()).isEqualTo(now);
        assertThat(result.uninstalledAt()).isNull();
        assertThat(result.purgeAfter()).isNull();
        assertThat(result.created()).isTrue();
    }

    @Test
    void maps_domain_to_uninstall_result() {
        Instant installedAt = Instant.parse("2025-01-15T10:30:00Z");
        Instant updatedAt = Instant.parse("2025-06-15T10:30:00Z");
        Instant uninstalledAt = Instant.parse("2025-06-15T10:30:00Z");
        Instant purgeAfter = Instant.parse("2025-07-15T10:30:00Z");
        Tenant tenant = Tenant.reconstitute(
                "cloud-1",
                new InstallationId("install-1"),
                new AppId("app-1"),
                "1.0.0",
                null,
                null,
                null,
                TenantStatus.UNINSTALLED,
                installedAt,
                updatedAt,
                uninstalledAt,
                purgeAfter);

        UninstallTenantResult result = TenantResultMapper.toUninstallResult(tenant);

        assertThat(result.tenantId()).isEqualTo("cloud-1");
        assertThat(result.installationId()).isEqualTo("install-1");
        assertThat(result.appId()).isEqualTo("app-1");
        assertThat(result.appVersion()).isEqualTo("1.0.0");
        assertThat(result.environmentId()).isNull();
        assertThat(result.siteUrl()).isNull();
        assertThat(result.installerAccountId()).isNull();
        assertThat(result.status()).isEqualTo(TenantStatus.UNINSTALLED);
        assertThat(result.installedAt()).isEqualTo(installedAt);
        assertThat(result.updatedAt()).isEqualTo(updatedAt);
        assertThat(result.uninstalledAt()).isEqualTo(uninstalledAt);
        assertThat(result.purgeAfter()).isEqualTo(purgeAfter);
    }
}
