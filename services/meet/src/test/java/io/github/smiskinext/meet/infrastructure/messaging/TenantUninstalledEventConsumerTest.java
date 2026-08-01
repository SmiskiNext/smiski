package io.github.smiskinext.meet.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.github.smiskinext.meet.application.command.HandleTenantUninstalledCommand;
import io.github.smiskinext.meet.application.usecase.HandleTenantUninstalledUseCase;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TenantUninstalledEventConsumerTest {

    private static CloudEvent validEvent(String protoJson) {
        return CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("tenant-service"))
                .withType("io.github.smiskinext.tenant.v1.uninstalled")
                .withDataContentType("application/json")
                .withData(protoJson.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    @Test
    void validEventDelegatesToUseCase() {
        HandleTenantUninstalledUseCase useCase = mock(HandleTenantUninstalledUseCase.class);
        TenantUninstalledEventConsumer consumer = new TenantUninstalledEventConsumer(useCase);
        String protoJson = "{\"cloudId\":\"cloud-xyz\",\"uninstalledAt\":\"2026-06-01T00:00:00Z\","
                + "\"purgeAfter\":\"2026-07-01T00:00:00Z\",\"updatedAt\":\"2026-06-01T00:00:00Z\"}";

        consumer.onMessage(validEvent(protoJson));

        ArgumentCaptor<HandleTenantUninstalledCommand> captor =
                forClass(HandleTenantUninstalledCommand.class);
        verify(useCase).handle(captor.capture());
        HandleTenantUninstalledCommand cmd = captor.getValue();
        assertThat(cmd.tenantId()).isEqualTo("cloud-xyz");
        assertThat(cmd.uninstalledAt()).isNotNull();
        assertThat(cmd.purgeAfter()).isNotNull();
    }

    @Test
    void nullDataThrowsIllegalArgument() {
        HandleTenantUninstalledUseCase useCase = mock(HandleTenantUninstalledUseCase.class);
        TenantUninstalledEventConsumer consumer = new TenantUninstalledEventConsumer(useCase);
        CloudEvent noData = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("tenant-service"))
                .withType("io.github.smiskinext.tenant.v1.uninstalled")
                .build();

        assertThatThrownBy(() -> consumer.onMessage(noData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no data");
    }

    @Test
    void missingCloudIdThrowsIllegalArgument() {
        HandleTenantUninstalledUseCase useCase = mock(HandleTenantUninstalledUseCase.class);
        TenantUninstalledEventConsumer consumer = new TenantUninstalledEventConsumer(useCase);
        String protoJson = "{\"uninstalledAt\":\"2026-06-01T00:00:00Z\"}";

        assertThatThrownBy(() -> consumer.onMessage(validEvent(protoJson)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cloudId");
    }

    @Test
    void malformedProtoJsonThrowsIllegalArgument() {
        HandleTenantUninstalledUseCase useCase = mock(HandleTenantUninstalledUseCase.class);
        TenantUninstalledEventConsumer consumer = new TenantUninstalledEventConsumer(useCase);

        assertThatThrownBy(() -> consumer.onMessage(validEvent("{invalid json}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed proto-JSON");
    }
}
