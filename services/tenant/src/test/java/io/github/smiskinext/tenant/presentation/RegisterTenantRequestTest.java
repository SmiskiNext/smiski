package io.github.smiskinext.tenant.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.tenant.application.command.RegisterTenantCommand;
import io.github.smiskinext.tenant.domain.model.EnvironmentType;
import io.github.smiskinext.tenant.presentation.request.RegisterTenantRequest;
import org.junit.jupiter.api.Test;

class RegisterTenantRequestTest {

    @Test
    void absent_environment_type_defaults_to_production() {
        RegisterTenantRequest request = new RegisterTenantRequest(
                "install-1",
                null,
                new RegisterTenantRequest.App("app-1", "1.0.0", null, null),
                null,
                null,
                null);

        RegisterTenantCommand command = request.toCommand("cloud-1");

        assertThat(command.environmentType()).isEqualTo(EnvironmentType.PRODUCTION);
    }

    @Test
    void optional_members_may_be_omitted() {
        RegisterTenantRequest request = new RegisterTenantRequest(
                "install-1",
                null,
                new RegisterTenantRequest.App("app-1", "1.0.0", null, null),
                null,
                null,
                null);

        RegisterTenantCommand command = request.toCommand("cloud-1");

        assertThat(command.installerAccountId()).isNull();
        assertThat(command.environmentId()).isNull();
        assertThat(command.siteUrl()).isNull();
    }
}
