package io.github.smiskinext.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.github.smiskinext.notification.application.command.HandleTenantInstalledCommand;
import io.github.smiskinext.notification.application.usecase.HandleTenantInstalledUseCase;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TenantInstalledEventConsumerTest {

    private final HandleTenantInstalledUseCase useCase = mock(HandleTenantInstalledUseCase.class);
    private final TenantInstalledEventConsumer consumer = new TenantInstalledEventConsumer(useCase);

    @Test
    void validProtoJsonDispatchesCommandWithCloudIdAndSiteUrl() {
        String json = """
                {"cloudId":"cloud-abc","siteUrl":"https://cloud-abc.atlassian.net","updatedAt":"2026-01-01T00:00:00Z"}""";

        consumer.onMessage(event(json));

        ArgumentCaptor<HandleTenantInstalledCommand> captor =
                forClass(HandleTenantInstalledCommand.class);
        verify(useCase).handle(captor.capture());
        HandleTenantInstalledCommand cmd = captor.getValue();
        assertThat(cmd.tenantId()).isEqualTo("cloud-abc");
        assertThat(cmd.cloudId()).isEqualTo("cloud-abc");
        assertThat(cmd.siteUrl()).isEqualTo("https://cloud-abc.atlassian.net");
    }

    @Test
    void blankSiteUrlMapsToNullInCommand() {
        String json = """
                {"cloudId":"cloud-blank","siteUrl":"","updatedAt":"2026-01-01T00:00:00Z"}""";

        consumer.onMessage(event(json));

        ArgumentCaptor<HandleTenantInstalledCommand> captor =
                forClass(HandleTenantInstalledCommand.class);
        verify(useCase).handle(captor.capture());
        assertThat(captor.getValue().siteUrl()).isNull();
    }

    @Test
    void malformedJsonThrowsException() {
        assertThatThrownBy(() -> consumer.onMessage(event("{invalid json}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed proto-JSON");
    }

    @Test
    void nullDataThrowsException() {
        CloudEvent noData = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("/tenant"))
                .withType("tenant.tenant.installed")
                .withTime(OffsetDateTime.now())
                .build();

        assertThatThrownBy(() -> consumer.onMessage(noData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no data");
    }

    private static CloudEvent event(String json) {
        return CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType("tenant.tenant.installed")
                .withSource(URI.create("/tenant"))
                .withDataContentType("application/json")
                .withTime(OffsetDateTime.now())
                .withData(
                        "application/json",
                        BytesCloudEventData.wrap(json.getBytes(StandardCharsets.UTF_8)))
                .build();
    }
}
