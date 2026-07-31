package io.github.smiskinext.meet.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.github.smiskinext.meet.application.command.HandleTenantInstalledCommand;
import io.github.smiskinext.meet.application.usecase.HandleTenantInstalledUseCase;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TenantInstalledEventConsumerTest {

    private static CloudEvent validEvent(String protoJson) {
        return CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("tenant-service"))
                .withType("io.github.smiskinext.tenant.v1.installed")
                .withDataContentType("application/json")
                .withData(protoJson.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    @Test
    void validEventDelegatesToUseCase() {
        HandleTenantInstalledUseCase useCase = mock(HandleTenantInstalledUseCase.class);
        TenantInstalledEventConsumer consumer = new TenantInstalledEventConsumer(useCase);
        String protoJson =
                "{\"cloudId\":\"cloud-abc\",\"installationId\":\"inst-1\",\"updatedAt\":\"2026-01-01T00:00:00Z\"}";

        consumer.onMessage(validEvent(protoJson));

        ArgumentCaptor<HandleTenantInstalledCommand> captor =
                forClass(HandleTenantInstalledCommand.class);
        verify(useCase).handle(captor.capture());
        HandleTenantInstalledCommand cmd = captor.getValue();
        assertThat(cmd.tenantId()).isEqualTo("cloud-abc");
        assertThat(cmd.cloudId()).isEqualTo("cloud-abc");
    }

    @Test
    void nullDataThrowsIllegalArgument() {
        HandleTenantInstalledUseCase useCase = mock(HandleTenantInstalledUseCase.class);
        TenantInstalledEventConsumer consumer = new TenantInstalledEventConsumer(useCase);
        CloudEvent noData = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("tenant-service"))
                .withType("io.github.smiskinext.tenant.v1.installed")
                .build();

        assertThatThrownBy(() -> consumer.onMessage(noData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no data");
    }

    @Test
    void missingCloudIdThrowsIllegalArgument() {
        HandleTenantInstalledUseCase useCase = mock(HandleTenantInstalledUseCase.class);
        TenantInstalledEventConsumer consumer = new TenantInstalledEventConsumer(useCase);
        String protoJson = "{\"installationId\":\"inst-1\"}";

        assertThatThrownBy(() -> consumer.onMessage(validEvent(protoJson)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cloudId");
    }

    @Test
    void malformedProtoJsonThrowsIllegalArgument() {
        HandleTenantInstalledUseCase useCase = mock(HandleTenantInstalledUseCase.class);
        TenantInstalledEventConsumer consumer = new TenantInstalledEventConsumer(useCase);

        assertThatThrownBy(() -> consumer.onMessage(validEvent("{invalid json}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed proto-JSON");
    }
}
