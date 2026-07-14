package io.github.smiskinext.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.tenant.application.mapper.TenantResultMapper;
import io.github.smiskinext.tenant.application.result.RegisterTenantResult;
import io.github.smiskinext.tenant.domain.model.Tenant;
import io.github.smiskinext.tenant.domain.model.TenantStatus;
import io.github.smiskinext.tenant.domain.model.valueobject.AppId;
import io.github.smiskinext.tenant.domain.model.valueobject.InstallationId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TenantResultMapperTest {

    @Test
    void maps_domain_to_result() {
        Instant now = Instant.now();
        Tenant tenant = Tenant.reconstitute(
                "cloud-1",
                new InstallationId("install-1"),
                new AppId("app-1"),
                "1.0.0",
                null,
                null,
                null,
                TenantStatus.ACTIVE,
                now,
                now,
                null,
                null);

        RegisterTenantResult result = TenantResultMapper.toResult(tenant, true);

        assertThat(result.tenantId()).isEqualTo("cloud-1");
        assertThat(result.installationId()).isEqualTo("install-1");
        assertThat(result.appId()).isEqualTo("app-1");
        assertThat(result.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(result.installedAt()).isEqualTo(now);
        assertThat(result.created()).isTrue();
    }
}
