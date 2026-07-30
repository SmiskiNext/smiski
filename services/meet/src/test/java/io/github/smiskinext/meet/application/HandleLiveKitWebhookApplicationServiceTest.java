package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.smiskinext.meet.application.service.HandleLiveKitWebhookApplicationService;
import io.github.smiskinext.meet.application.service.LiveKitWebhookProcessingApplicationService;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitWebhookEvent;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HandleLiveKitWebhookApplicationServiceTest {

    private LiveKitWebhookProcessingApplicationService processingService;
    private HandleLiveKitWebhookApplicationService service;

    @BeforeEach
    void setUp() {
        processingService = mock(LiveKitWebhookProcessingApplicationService.class);
        service = new HandleLiveKitWebhookApplicationService(processingService);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void bindsTenantFromRoomMetadataDuringProcessingAndClearsAfterward() {
        AtomicReference<String> boundTenant = new AtomicReference<>();
        doAnswer(invocation -> {
                    boundTenant.set(TenantContext.getCurrentTenant());
                    return null;
                })
                .when(processingService)
                .process(any());

        service.handle(event("tenant-42"));

        assertThat(boundTenant.get()).isEqualTo("tenant-42");
        assertThat(TenantContext.getCurrentTenant()).isEqualTo(TenantContext.DEFAULT_TENANT);
        verify(processingService).process(any());
    }

    @Test
    void missingTenantMetadataPerformsNoTenantScopedWrite() {
        service.handle(event(null));
        service.handle(event(""));

        verify(processingService, never()).process(any());
        assertThat(TenantContext.getCurrentTenant()).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    private LiveKitWebhookEvent event(String metadata) {
        return new LiveKitWebhookEvent(
                "room_started",
                "meeting-" + UUID.randomUUID(),
                metadata,
                null,
                null,
                Map.of(),
                null,
                null,
                UUID.randomUUID().toString(),
                Instant.parse("2025-02-01T14:00:00Z"));
    }
}
