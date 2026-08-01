package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.smiskinext.meet.application.command.HandleTenantUninstalledCommand;
import io.github.smiskinext.meet.application.service.HandleTenantUninstalledApplicationService;
import io.github.smiskinext.meet.domain.model.TenantRecord;
import io.github.smiskinext.meet.domain.model.TenantStatus;
import io.github.smiskinext.meet.domain.port.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HandleTenantUninstalledApplicationServiceTest {

    @Test
    void upsertCalledWithUninstalledStatusAndTimestamps() {
        TenantRepository repository = mock(TenantRepository.class);
        HandleTenantUninstalledApplicationService service =
                new HandleTenantUninstalledApplicationService(repository);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant purgeAfter = now.plus(30, ChronoUnit.DAYS);

        service.handle(
                new HandleTenantUninstalledCommand("cloud-xyz", "cloud-xyz", now, now, purgeAfter));

        ArgumentCaptor<TenantRecord> captor = forClass(TenantRecord.class);
        verify(repository).upsert(captor.capture());
        TenantRecord saved = captor.getValue();
        assertThat(saved.tenantId()).isEqualTo("cloud-xyz");
        assertThat(saved.status()).isEqualTo(TenantStatus.UNINSTALLED);
        assertThat(saved.uninstalledAt()).isEqualTo(now);
        assertThat(saved.purgeAfter()).isEqualTo(purgeAfter);
    }

    @Test
    void upsertCalledWithNullTimestampsWhenAbsent() {
        TenantRepository repository = mock(TenantRepository.class);
        HandleTenantUninstalledApplicationService service =
                new HandleTenantUninstalledApplicationService(repository);
        Instant now = Instant.now();

        service.handle(
                new HandleTenantUninstalledCommand("cloud-xyz", "cloud-xyz", now, null, null));

        ArgumentCaptor<TenantRecord> captor = forClass(TenantRecord.class);
        verify(repository).upsert(captor.capture());
        TenantRecord saved = captor.getValue();
        assertThat(saved.status()).isEqualTo(TenantStatus.UNINSTALLED);
        assertThat(saved.uninstalledAt()).isNull();
        assertThat(saved.purgeAfter()).isNull();
    }
}
