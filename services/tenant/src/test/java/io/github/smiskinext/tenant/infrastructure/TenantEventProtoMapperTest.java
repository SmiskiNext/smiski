package io.github.smiskinext.tenant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import com.google.protobuf.Message;
import io.github.smiskinext.event.tenant.v1.TenantInstalled;
import io.github.smiskinext.tenant.domain.event.TenantInstalledEvent;
import io.github.smiskinext.tenant.domain.model.TenantStatus;
import io.github.smiskinext.tenant.infrastructure.messaging.TenantEventProtoMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TenantEventProtoMapperTest {

    private final TenantEventProtoMapper mapper = new TenantEventProtoMapper();

    @Test
    void returns_correct_event_type_class() {
        assertThat(mapper.eventType()).isEqualTo(TenantInstalledEvent.class);
    }

    @Test
    void returns_correct_data_schema() {
        assertThat(mapper.dataSchema())
                .isEqualTo("io.github.smiskinext.event.tenant.v1.TenantInstalled");
    }

    @Test
    void maps_domain_event_to_proto_with_matching_fields() {
        Instant now = Instant.now();
        TenantInstalledEvent event = new TenantInstalledEvent(
                UuidCreator.getTimeOrderedEpoch(),
                "cloud-123",
                "install-1",
                "app-1",
                "1.0.0",
                "env-1",
                "https://example.atlassian.net",
                "installer-1",
                now,
                TenantStatus.ACTIVE,
                now,
                null,
                null);

        Message result = mapper.toProto(event);
        assertThat(result).isInstanceOf(TenantInstalled.class);
        TenantInstalled proto = (TenantInstalled) result;

        assertThat(proto.getCloudId()).isEqualTo("cloud-123");
        assertThat(proto.getInstallationId()).isEqualTo("install-1");
        assertThat(proto.getAppId()).isEqualTo("app-1");
        assertThat(proto.getAppVersion()).isEqualTo("1.0.0");
        assertThat(proto.getEnvironmentId()).isEqualTo("env-1");
        assertThat(proto.getSiteUrl()).isEqualTo("https://example.atlassian.net");
        assertThat(proto.getInstallerAccountId()).isEqualTo("installer-1");
        assertThat(proto.getInstalledAt()).isEqualTo(now.toString());
        assertThat(proto.getStatus()).isEqualTo("ACTIVE");
        assertThat(proto.getUpdatedAt()).isEqualTo(now.toString());
        assertThat(proto.getUninstalledAt()).isEmpty();
        assertThat(proto.getPurgeAfter()).isEmpty();
    }

    @Test
    void handles_null_optional_fields() {
        Instant now = Instant.now();
        TenantInstalledEvent event = new TenantInstalledEvent(
                UuidCreator.getTimeOrderedEpoch(),
                "cloud-123",
                "install-1",
                "app-1",
                null,
                null,
                null,
                null,
                now,
                TenantStatus.ACTIVE,
                now,
                null,
                null);

        TenantInstalled proto = (TenantInstalled) mapper.toProto(event);

        assertThat(proto.getCloudId()).isEqualTo("cloud-123");
        assertThat(proto.getAppVersion()).isEmpty();
        assertThat(proto.getEnvironmentId()).isEmpty();
        assertThat(proto.getUninstalledAt()).isEmpty();
        assertThat(proto.getPurgeAfter()).isEmpty();
    }
}
