package io.github.smiskinext.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.tenant.application.mapper.TenantResponseMapper;
import io.github.smiskinext.tenant.application.response.TenantResponse;
import io.github.smiskinext.tenant.domain.model.EnvironmentType;
import io.github.smiskinext.tenant.domain.model.Tenant;
import io.github.smiskinext.tenant.domain.model.TenantStatus;
import io.github.smiskinext.tenant.domain.model.valueobject.AppId;
import io.github.smiskinext.tenant.domain.model.valueobject.InstallationId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TenantResponseMapperTest {

    @Test
    void maps_domain_to_response_without_envelope() {
        Instant now = Instant.now();
        Tenant tenant = Tenant.reconstitute(
                "cloud-1",
                new InstallationId("install-1"),
                new AppId("app-1"),
                "1.0.0",
                null,
                EnvironmentType.PRODUCTION,
                null,
                null,
                TenantStatus.ACTIVE,
                now,
                now,
                null,
                null);

        TenantResponse response = TenantResponseMapper.toResponse(tenant, true);

        assertThat(response.tenantId()).isEqualTo("cloud-1");
        assertThat(response.installationId()).isEqualTo("install-1");
        assertThat(response.appId()).isEqualTo("app-1");
        assertThat(response.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(response.environmentType()).isEqualTo(EnvironmentType.PRODUCTION);
        assertThat(response.installedAt()).isEqualTo(now);
    }
}
