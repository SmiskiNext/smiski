package io.github.smiskinext.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import io.github.smiskinext.tenant.application.command.RegisterTenantCommand;
import io.github.smiskinext.tenant.application.response.TenantResponse;
import io.github.smiskinext.tenant.application.service.RegisterTenantApplicationService;
import io.github.smiskinext.tenant.domain.TenantError;
import io.github.smiskinext.tenant.domain.event.PublishableEvent;
import io.github.smiskinext.tenant.domain.model.EnvironmentType;
import io.github.smiskinext.tenant.domain.model.Tenant;
import io.github.smiskinext.tenant.domain.model.TenantStatus;
import io.github.smiskinext.tenant.domain.model.valueobject.AppId;
import io.github.smiskinext.tenant.domain.model.valueobject.InstallationId;
import io.github.smiskinext.tenant.domain.port.EventPublisher;
import io.github.smiskinext.tenant.domain.port.TenantRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterTenantApplicationServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private EventPublisher eventPublisher;

    private RegisterTenantApplicationService service;

    @BeforeEach
    void setUp() {
        service = new RegisterTenantApplicationService(tenantRepository, eventPublisher);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void rejects_default_tenant_context() {
        RegisterTenantCommand command = new RegisterTenantCommand(
                TenantContext.DEFAULT_TENANT,
                "install-1",
                "app-1",
                "1.0.0",
                null,
                EnvironmentType.PRODUCTION,
                null,
                null);

        Result<TenantResponse, TenantError> result = service.execute(command);

        assertThat(result).isInstanceOf(Result.Failure.class);
        TenantError error = ((Result.Failure<TenantResponse, TenantError>) result).error();
        assertThat(error).isInstanceOf(TenantError.MissingTenantContext.class);
        verify(tenantRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void creates_new_tenant_and_publishes_event() {
        when(tenantRepository.findById("cloud-abc")).thenReturn(Optional.empty());
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterTenantCommand command = new RegisterTenantCommand(
                "cloud-abc",
                "install-1",
                "app-1",
                "1.0.0",
                null,
                EnvironmentType.PRODUCTION,
                null,
                null);

        Result<TenantResponse, TenantError> result = service.execute(command);

        assertThat(result).isInstanceOf(Result.Success.class);
        TenantResponse response = ((Result.Success<TenantResponse, TenantError>) result).value();
        assertThat(response.tenantId()).isEqualTo("cloud-abc");
        assertThat(response.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(response.created()).isTrue();

        ArgumentCaptor<PublishableEvent> eventCaptor =
                ArgumentCaptor.forClass(PublishableEvent.class);
        verify(eventPublisher, times(1)).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().aggregateId()).isEqualTo("cloud-abc");
    }

    @Test
    void updates_existing_tenant_and_publishes_event() {
        Tenant existing = Tenant.reconstitute(
                "cloud-abc",
                new InstallationId("old-install"),
                new AppId("app-1"),
                "1.0.0",
                null,
                EnvironmentType.PRODUCTION,
                null,
                null,
                TenantStatus.ACTIVE,
                Instant.now().minusSeconds(3600),
                Instant.now().minusSeconds(3600),
                null,
                null);
        when(tenantRepository.findById("cloud-abc")).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterTenantCommand command = new RegisterTenantCommand(
                "cloud-abc",
                "new-install",
                "app-1",
                "2.0.0",
                null,
                EnvironmentType.PRODUCTION,
                null,
                null);

        Result<TenantResponse, TenantError> result = service.execute(command);

        assertThat(result).isInstanceOf(Result.Success.class);
        TenantResponse response = ((Result.Success<TenantResponse, TenantError>) result).value();
        assertThat(response.installationId()).isEqualTo("new-install");
        assertThat(response.created()).isFalse();

        verify(eventPublisher, times(1)).publish(any());
    }
}
