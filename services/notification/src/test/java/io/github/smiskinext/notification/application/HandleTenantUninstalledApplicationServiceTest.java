package io.github.smiskinext.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.smiskinext.notification.application.command.HandleTenantUninstalledCommand;
import io.github.smiskinext.notification.application.service.HandleTenantUninstalledApplicationService;
import io.github.smiskinext.notification.domain.model.TenantProjection;
import io.github.smiskinext.notification.domain.model.TenantStatus;
import io.github.smiskinext.notification.domain.port.TenantProjectionRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HandleTenantUninstalledApplicationServiceTest {

    private final TenantProjectionRepository repository = mock(TenantProjectionRepository.class);
    private final HandleTenantUninstalledApplicationService service =
            new HandleTenantUninstalledApplicationService(repository);

    @Test
    void upsertMarksTenantUninstalledWithTimestamps() {
        Instant updatedAt = Instant.parse("2026-03-01T10:00:00Z");
        Instant uninstalledAt = Instant.parse("2026-03-01T09:55:00Z");
        Instant purgeAfter = Instant.parse("2026-06-01T00:00:00Z");

        service.handle(new HandleTenantUninstalledCommand(
                "tenant-1", "cloud-1", updatedAt, uninstalledAt, purgeAfter));

        ArgumentCaptor<TenantProjection> captor = forClass(TenantProjection.class);
        verify(repository).upsert(captor.capture());
        TenantProjection saved = captor.getValue();
        assertThat(saved.tenantId()).isEqualTo("tenant-1");
        assertThat(saved.cloudId()).isEqualTo("cloud-1");
        assertThat(saved.siteUrl()).isNull();
        assertThat(saved.status()).isEqualTo(TenantStatus.UNINSTALLED);
        assertThat(saved.updatedAt()).isEqualTo(updatedAt);
        assertThat(saved.uninstalledAt()).isEqualTo(uninstalledAt);
        assertThat(saved.purgeAfter()).isEqualTo(purgeAfter);
    }

    @Test
    void nullableTimestampsArePassedThrough() {
        Instant updatedAt = Instant.now();

        service.handle(
                new HandleTenantUninstalledCommand("tenant-2", "cloud-2", updatedAt, null, null));

        ArgumentCaptor<TenantProjection> captor = forClass(TenantProjection.class);
        verify(repository).upsert(captor.capture());
        TenantProjection saved = captor.getValue();
        assertThat(saved.uninstalledAt()).isNull();
        assertThat(saved.purgeAfter()).isNull();
        assertThat(saved.status()).isEqualTo(TenantStatus.UNINSTALLED);
    }
}
