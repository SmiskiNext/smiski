package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.smiskinext.meet.application.command.HandleTenantInstalledCommand;
import io.github.smiskinext.meet.application.service.HandleTenantInstalledApplicationService;
import io.github.smiskinext.meet.domain.model.TenantRecord;
import io.github.smiskinext.meet.domain.model.TenantStatus;
import io.github.smiskinext.meet.domain.port.TenantRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HandleTenantInstalledApplicationServiceTest {

    private final TenantRepository repository = mock(TenantRepository.class);
    private final HandleTenantInstalledApplicationService service =
            new HandleTenantInstalledApplicationService(repository);

    @Test
    void upsertCalledWithActiveStatusOnInstall() {
        Instant now = Instant.now();

        service.handle(new HandleTenantInstalledCommand(
                "cloud-abc", "cloud-abc", "https://site.atlassian.net", now));

        ArgumentCaptor<TenantRecord> captor = forClass(TenantRecord.class);
        verify(repository).upsert(captor.capture());
        TenantRecord saved = captor.getValue();
        assertThat(saved.tenantId()).isEqualTo("cloud-abc");
        assertThat(saved.cloudId()).isEqualTo("cloud-abc");
        assertThat(saved.siteUrl()).isEqualTo("https://site.atlassian.net");
        assertThat(saved.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(saved.updatedAt()).isEqualTo(now);
        assertThat(saved.uninstalledAt()).isNull();
        assertThat(saved.purgeAfter()).isNull();
    }

    @Test
    void nullSiteUrlIsPersistedAsNull() {
        Instant now = Instant.now();

        service.handle(new HandleTenantInstalledCommand("cloud-xyz", "cloud-xyz", null, now));

        ArgumentCaptor<TenantRecord> captor = forClass(TenantRecord.class);
        verify(repository).upsert(captor.capture());
        TenantRecord saved = captor.getValue();
        assertThat(saved.siteUrl()).isNull();
    }
}
