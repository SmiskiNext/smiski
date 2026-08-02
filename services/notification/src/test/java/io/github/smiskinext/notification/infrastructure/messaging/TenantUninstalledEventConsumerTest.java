package io.github.smiskinext.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.github.smiskinext.notification.application.command.HandleTenantUninstalledCommand;
import io.github.smiskinext.notification.application.usecase.HandleTenantUninstalledUseCase;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TenantUninstalledEventConsumerTest {

    private final HandleTenantUninstalledUseCase useCase =
            mock(HandleTenantUninstalledUseCase.class);
    private final TenantUninstalledEventConsumer consumer =
            new TenantUninstalledEventConsumer(useCase);

    @Test
    void validProtoJsonDispatchesCommand() {
        String json = """
                {"cloudId":"cloud-abc","updatedAt":"2026-03-01T10:00:00Z","uninstalledAt":"2026-03-01T09:55:00Z","purgeAfter":"2026-06-01T00:00:00Z"}""";

        consumer.onMessage(event(json));

        ArgumentCaptor<HandleTenantUninstalledCommand> captor =
                forClass(HandleTenantUninstalledCommand.class);
        verify(useCase).handle(captor.capture());
        HandleTenantUninstalledCommand cmd = captor.getValue();
        assertThat(cmd.tenantId()).isEqualTo("cloud-abc");
        assertThat(cmd.cloudId()).isEqualTo("cloud-abc");
    }

    @Test
    void malformedJsonIsSkippedWithoutException() {
        assertThatCode(() -> consumer.onMessage(event("{bad json}"))).doesNotThrowAnyException();

        verifyNoInteractions(useCase);
    }

    @Test
    void nullDataIsSkippedWithoutException() {
        CloudEvent noData = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("/tenant"))
                .withType("tenant.tenant.uninstalled")
                .withTime(OffsetDateTime.now())
                .build();

        assertThatCode(() -> consumer.onMessage(noData)).doesNotThrowAnyException();

        verifyNoInteractions(useCase);
    }

    private static CloudEvent event(String json) {
        return CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType("tenant.tenant.uninstalled")
                .withSource(URI.create("/tenant"))
                .withDataContentType("application/json")
                .withTime(OffsetDateTime.now())
                .withData(
                        "application/json",
                        BytesCloudEventData.wrap(json.getBytes(StandardCharsets.UTF_8)))
                .build();
    }
}
