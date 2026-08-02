package io.github.smiskinext.meet.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import io.github.smiskinext.meet.domain.model.TenantRecord;
import io.github.smiskinext.meet.domain.model.TenantStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class TenantRepositoryAdapterIntegrationTest {

    @Autowired
    private TenantRepositoryAdapter tenantRepositoryAdapter;

    @Autowired
    private TenantJpaRepository tenantJpaRepository;

    @Test
    void upsertInsertsNewTenantWithActiveStatus() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        TenantRecord record = new TenantRecord(
                "cloud-new",
                "cloud-new",
                "https://cloud-new.atlassian.net",
                TenantStatus.ACTIVE,
                now,
                null,
                null);

        tenantRepositoryAdapter.upsert(record);

        TenantJpaEntity entity = tenantJpaRepository.findById("cloud-new").orElseThrow();
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getCloudId()).isEqualTo("cloud-new");
        assertThat(entity.getSiteUrl()).isEqualTo("https://cloud-new.atlassian.net");
        assertThat(entity.getUninstalledAt()).isNull();
        assertThat(entity.getPurgeAfter()).isNull();
    }

    @Test
    void upsertUpdatesExistingTenantToUninstalled() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant purgeAfter = now.plus(30, ChronoUnit.DAYS);
        tenantRepositoryAdapter.upsert(new TenantRecord(
                "cloud-up", "cloud-up", null, TenantStatus.ACTIVE, now, null, null));

        tenantRepositoryAdapter.upsert(new TenantRecord(
                "cloud-up", "cloud-up", null, TenantStatus.UNINSTALLED, now, now, purgeAfter));

        TenantJpaEntity entity = tenantJpaRepository.findById("cloud-up").orElseThrow();
        assertThat(entity.getStatus()).isEqualTo("UNINSTALLED");
        assertThat(entity.getUninstalledAt()).isEqualTo(now);
        assertThat(entity.getPurgeAfter()).isEqualTo(purgeAfter);
        assertThat(tenantJpaRepository.findAll().stream()
                        .filter(e -> "cloud-up".equals(e.getTenantId()))
                        .count())
                .isEqualTo(1);
    }

    @Test
    void upsertReactivatesUninstalledTenant() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant purgeAfter = now.plus(30, ChronoUnit.DAYS);
        tenantRepositoryAdapter.upsert(new TenantRecord(
                "cloud-re", "cloud-re", null, TenantStatus.UNINSTALLED, now, now, purgeAfter));

        tenantRepositoryAdapter.upsert(new TenantRecord(
                "cloud-re", "cloud-re", null, TenantStatus.ACTIVE, now, null, null));

        TenantJpaEntity entity = tenantJpaRepository.findById("cloud-re").orElseThrow();
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getUninstalledAt()).isNull();
        assertThat(entity.getPurgeAfter()).isNull();
    }

    @Test
    void upsertPersistsNullSiteUrlAsNull() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        TenantRecord record = new TenantRecord(
                "cloud-null-url", "cloud-null-url", null, TenantStatus.ACTIVE, now, null, null);

        tenantRepositoryAdapter.upsert(record);

        TenantJpaEntity entity = tenantJpaRepository.findById("cloud-null-url").orElseThrow();
        assertThat(entity.getSiteUrl()).isNull();
    }
}
