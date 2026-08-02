package io.github.smiskinext.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.smiskinext.notification.application.command.HandleTenantInstalledCommand;
import io.github.smiskinext.notification.application.service.HandleTenantInstalledApplicationService;
import io.github.smiskinext.notification.domain.model.TenantProjection;
import io.github.smiskinext.notification.domain.model.TenantStatus;
import io.github.smiskinext.notification.domain.port.TenantProjectionRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HandleTenantInstalledApplicationServiceTest {

    private final TenantProjectionRepository repository = mock(TenantProjectionRepository.class);
    private final HandleTenantInstalledApplicationService service =
            new HandleTenantInstalledApplicationService(repository);

    @Test
    void upsertCreatesRowWithSiteUrlAndActiveStatus() {
        Instant now = Instant.now();

        service.handle(new HandleTenantInstalledCommand(
                "tenant-1", "cloud-1", "https://site.atlassian.net", now));

        ArgumentCaptor<TenantProjection> captor = forClass(TenantProjection.class);
        verify(repository).upsert(captor.capture());
        TenantProjection saved = captor.getValue();
        assertThat(saved.tenantId()).isEqualTo("tenant-1");
        assertThat(saved.cloudId()).isEqualTo("cloud-1");
        assertThat(saved.siteUrl()).isEqualTo("https://site.atlassian.net");
        assertThat(saved.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(saved.updatedAt()).isEqualTo(now);
        assertThat(saved.uninstalledAt()).isNull();
        assertThat(saved.purgeAfter()).isNull();
    }

    @Test
    void nullSiteUrlIsPersistedAsNull() {
        Instant now = Instant.now();

        service.handle(new HandleTenantInstalledCommand("tenant-2", "cloud-2", null, now));

        ArgumentCaptor<TenantProjection> captor = forClass(TenantProjection.class);
        verify(repository).upsert(captor.capture());
        assertThat(captor.getValue().siteUrl()).isNull();
        assertThat(captor.getValue().status()).isEqualTo(TenantStatus.ACTIVE);
    }
}
