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

    @Test
    void upsertCalledWithActiveStatusOnInstall() {
        TenantRepository repository = mock(TenantRepository.class);
        HandleTenantInstalledApplicationService service =
                new HandleTenantInstalledApplicationService(repository);
        Instant now = Instant.now();

        service.handle(new HandleTenantInstalledCommand("cloud-abc", "cloud-abc", now));

        ArgumentCaptor<TenantRecord> captor = forClass(TenantRecord.class);
        verify(repository).upsert(captor.capture());
        TenantRecord saved = captor.getValue();
        assertThat(saved.tenantId()).isEqualTo("cloud-abc");
        assertThat(saved.cloudId()).isEqualTo("cloud-abc");
        assertThat(saved.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(saved.updatedAt()).isEqualTo(now);
        assertThat(saved.uninstalledAt()).isNull();
        assertThat(saved.purgeAfter()).isNull();
    }
}
