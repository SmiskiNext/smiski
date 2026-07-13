package io.github.smiskinext.tenant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import com.google.protobuf.Message;
import io.github.smiskinext.event.tenant.v1.TenantInstalled;
import io.github.smiskinext.tenant.domain.event.TenantInstalledEvent;
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
                "PRODUCTION",
                "https://example.atlassian.net",
                "installer-1",
                now);

        Message result = mapper.toProto(event);
        assertThat(result).isInstanceOf(TenantInstalled.class);
        TenantInstalled proto = (TenantInstalled) result;

        assertThat(proto.getCloudId()).isEqualTo("cloud-123");
        assertThat(proto.getInstallationId()).isEqualTo("install-1");
        assertThat(proto.getAppId()).isEqualTo("app-1");
        assertThat(proto.getAppVersion()).isEqualTo("1.0.0");
        assertThat(proto.getEnvironmentId()).isEqualTo("env-1");
        assertThat(proto.getEnvironmentType()).isEqualTo("PRODUCTION");
        assertThat(proto.getSiteUrl()).isEqualTo("https://example.atlassian.net");
        assertThat(proto.getInstallerAccountId()).isEqualTo("installer-1");
        assertThat(proto.getInstalledAt()).isEqualTo(now.toString());
    }

    @Test
    void handles_null_optional_fields() {
        TenantInstalledEvent event = new TenantInstalledEvent(
                UuidCreator.getTimeOrderedEpoch(),
                "cloud-123",
                "install-1",
                "app-1",
                null,
                null,
                "PRODUCTION",
                null,
                null,
                Instant.now());

        TenantInstalled proto = (TenantInstalled) mapper.toProto(event);

        assertThat(proto.getCloudId()).isEqualTo("cloud-123");
        assertThat(proto.getAppVersion()).isEmpty();
        assertThat(proto.getEnvironmentId()).isEmpty();
    }
}
