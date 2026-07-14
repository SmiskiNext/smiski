package io.github.smiskinext.tenant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import com.google.protobuf.Message;
import io.github.smiskinext.event.tenant.v1.TenantUninstalled;
import io.github.smiskinext.tenant.domain.event.TenantUninstalledEvent;
import io.github.smiskinext.tenant.domain.model.TenantStatus;
import io.github.smiskinext.tenant.infrastructure.messaging.TenantUninstalledEventProtoMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TenantUninstalledEventProtoMapperTest {

    private final TenantUninstalledEventProtoMapper mapper =
            new TenantUninstalledEventProtoMapper();

    @Test
    void returns_correct_event_type_class() {
        assertThat(mapper.eventType()).isEqualTo(TenantUninstalledEvent.class);
    }

    @Test
    void returns_correct_data_schema() {
        assertThat(mapper.dataSchema())
                .isEqualTo("io.github.smiskinext.event.tenant.v1.TenantUninstalled");
    }

    @Test
    void maps_domain_event_to_proto_with_all_fields() {
        Instant installedAt = Instant.parse("2025-01-15T10:30:00Z");
        Instant updatedAt = Instant.parse("2025-06-15T10:30:00Z");
        Instant uninstalledAt = Instant.parse("2025-06-15T10:30:00Z");
        Instant purgeAfter = Instant.parse("2025-07-15T10:30:00Z");
        TenantUninstalledEvent event = new TenantUninstalledEvent(
                UuidCreator.getTimeOrderedEpoch(),
                "cloud-123",
                "install-1",
                "app-1",
                uninstalledAt,
                purgeAfter,
                "1.0.0",
                "env-1",
                "https://example.atlassian.net",
                "installer-1",
                TenantStatus.UNINSTALLED,
                installedAt,
                updatedAt);

        Message result = mapper.toProto(event);
        assertThat(result).isInstanceOf(TenantUninstalled.class);
        TenantUninstalled proto = (TenantUninstalled) result;

        assertThat(proto.getCloudId()).isEqualTo("cloud-123");
        assertThat(proto.getInstallationId()).isEqualTo("install-1");
        assertThat(proto.getAppId()).isEqualTo("app-1");
        assertThat(proto.getUninstalledAt()).isEqualTo(uninstalledAt.toString());
        assertThat(proto.getPurgeAfter()).isEqualTo(purgeAfter.toString());
        assertThat(proto.getAppVersion()).isEqualTo("1.0.0");
        assertThat(proto.getEnvironmentId()).isEqualTo("env-1");
        assertThat(proto.getSiteUrl()).isEqualTo("https://example.atlassian.net");
        assertThat(proto.getInstallerAccountId()).isEqualTo("installer-1");
        assertThat(proto.getStatus()).isEqualTo("UNINSTALLED");
        assertThat(proto.getInstalledAt()).isEqualTo(installedAt.toString());
        assertThat(proto.getUpdatedAt()).isEqualTo(updatedAt.toString());
    }

    @Test
    void handles_null_optional_fields() {
        Instant installedAt = Instant.parse("2025-01-15T10:30:00Z");
        Instant updatedAt = Instant.parse("2025-06-15T10:30:00Z");
        Instant uninstalledAt = Instant.parse("2025-06-15T10:30:00Z");
        Instant purgeAfter = Instant.parse("2025-07-15T10:30:00Z");
        TenantUninstalledEvent event = new TenantUninstalledEvent(
                UuidCreator.getTimeOrderedEpoch(),
                "cloud-123",
                "install-1",
                "app-1",
                uninstalledAt,
                purgeAfter,
                null,
                null,
                null,
                null,
                TenantStatus.UNINSTALLED,
                installedAt,
                updatedAt);

        TenantUninstalled proto = (TenantUninstalled) mapper.toProto(event);

        assertThat(proto.getAppVersion()).isEmpty();
        assertThat(proto.getEnvironmentId()).isEmpty();
        assertThat(proto.getSiteUrl()).isEmpty();
        assertThat(proto.getInstallerAccountId()).isEmpty();
        assertThat(proto.getStatus()).isEqualTo("UNINSTALLED");
        assertThat(proto.getInstalledAt()).isEqualTo(installedAt.toString());
        assertThat(proto.getUpdatedAt()).isEqualTo(updatedAt.toString());
    }
}
