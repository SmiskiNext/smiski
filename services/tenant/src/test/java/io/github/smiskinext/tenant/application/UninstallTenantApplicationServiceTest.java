package io.github.smiskinext.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.PublishableEvent;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import io.github.smiskinext.tenant.application.command.UninstallTenantCommand;
import io.github.smiskinext.tenant.application.result.UninstallTenantResult;
import io.github.smiskinext.tenant.application.service.UninstallTenantApplicationService;
import io.github.smiskinext.tenant.domain.TenantError;
import io.github.smiskinext.tenant.domain.model.Tenant;
import io.github.smiskinext.tenant.domain.model.TenantStatus;
import io.github.smiskinext.tenant.domain.model.valueobject.AppId;
import io.github.smiskinext.tenant.domain.model.valueobject.InstallationId;
import io.github.smiskinext.tenant.domain.port.PurgePolicy;
import io.github.smiskinext.tenant.domain.port.TenantRepository;
import java.time.Duration;
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
class UninstallTenantApplicationServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private PurgePolicy purgePolicy;

    private UninstallTenantApplicationService service;

    @BeforeEach
    void setUp() {
        service = new UninstallTenantApplicationService(
                tenantRepository, eventPublisher, purgePolicy);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void active_tenant_marked_uninstalled_with_purge_after() {
        Instant installedAt = Instant.now().minusSeconds(3600);
        Tenant existing = Tenant.reconstitute(
                "cloud-abc",
                new InstallationId("install-1"),
                new AppId("app-1"),
                "1.0.0",
                null,
                null,
                null,
                TenantStatus.ACTIVE,
                installedAt,
                installedAt,
                null,
                null);
        when(tenantRepository.findById("cloud-abc")).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purgePolicy.retention()).thenReturn(Duration.ofDays(30));

        UninstallTenantCommand command = new UninstallTenantCommand("cloud-abc");
        Result<UninstallTenantResult, TenantError> result = service.execute(command);

        assertThat(result).isInstanceOf(Result.Success.class);
        UninstallTenantResult response =
                ((Result.Success<UninstallTenantResult, TenantError>) result).value();
        assertThat(response.tenantId()).isEqualTo("cloud-abc");
        assertThat(response.installationId()).isEqualTo("install-1");
        assertThat(response.appId()).isEqualTo("app-1");
        assertThat(response.appVersion()).isEqualTo("1.0.0");
        assertThat(response.status()).isEqualTo(TenantStatus.UNINSTALLED);
        assertThat(response.installedAt()).isEqualTo(installedAt);
        assertThat(response.updatedAt()).isNotNull();
        assertThat(response.uninstalledAt()).isNotNull();
        assertThat(response.purgeAfter()).isAfter(response.uninstalledAt());

        ArgumentCaptor<PublishableEvent> eventCaptor =
                ArgumentCaptor.forClass(PublishableEvent.class);
        verify(eventPublisher, times(1)).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().aggregateId()).isEqualTo("cloud-abc");
        assertThat(eventCaptor.getValue().eventType())
                .isEqualTo("io.github.smiskinext.tenant.v1.uninstalled");
    }

    @Test
    void unknown_cloud_id_returns_tenant_not_found() {
        when(tenantRepository.findById("cloud-unknown")).thenReturn(Optional.empty());

        UninstallTenantCommand command = new UninstallTenantCommand("cloud-unknown");
        Result<UninstallTenantResult, TenantError> result = service.execute(command);

        assertThat(result).isInstanceOf(Result.Failure.class);
        TenantError error = ((Result.Failure<UninstallTenantResult, TenantError>) result).error();
        assertThat(error).isInstanceOf(TenantError.TenantNotFound.class);
        verify(tenantRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void already_uninstalled_returns_current_representation_without_state_change() {
        Instant existingUninstalledAt = Instant.now().minusSeconds(1800);
        Instant existingPurgeAfter = existingUninstalledAt.plus(Duration.ofDays(30));
        Instant installedAt = Instant.now().minusSeconds(7200);
        Instant updatedAt = Instant.now().minusSeconds(1800);
        Tenant existing = Tenant.reconstitute(
                "cloud-abc",
                new InstallationId("install-1"),
                new AppId("app-1"),
                "1.0.0",
                null,
                null,
                null,
                TenantStatus.UNINSTALLED,
                installedAt,
                updatedAt,
                existingUninstalledAt,
                existingPurgeAfter);
        when(tenantRepository.findById("cloud-abc")).thenReturn(Optional.of(existing));

        UninstallTenantCommand command = new UninstallTenantCommand("cloud-abc");
        Result<UninstallTenantResult, TenantError> result = service.execute(command);

        assertThat(result).isInstanceOf(Result.Success.class);
        UninstallTenantResult response =
                ((Result.Success<UninstallTenantResult, TenantError>) result).value();
        assertThat(response.status()).isEqualTo(TenantStatus.UNINSTALLED);
        assertThat(response.installationId()).isEqualTo("install-1");
        assertThat(response.appId()).isEqualTo("app-1");
        assertThat(response.installedAt()).isEqualTo(installedAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
        assertThat(response.uninstalledAt()).isEqualTo(existingUninstalledAt);
        assertThat(response.purgeAfter()).isEqualTo(existingPurgeAfter);

        verify(tenantRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void rejects_default_tenant_context() {
        UninstallTenantCommand command = new UninstallTenantCommand(TenantContext.DEFAULT_TENANT);
        Result<UninstallTenantResult, TenantError> result = service.execute(command);

        assertThat(result).isInstanceOf(Result.Failure.class);
        TenantError error = ((Result.Failure<UninstallTenantResult, TenantError>) result).error();
        assertThat(error).isInstanceOf(TenantError.MissingTenantContext.class);
        verify(tenantRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }
}
