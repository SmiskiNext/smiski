package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.usecase.HandleLiveKitWebhookUseCase;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitWebhookEvent;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves the owning tenant from the webhook event's room metadata and binds it for the duration
 * of the processing transaction, clearing it afterward. The tenant must be bound before the
 * transactional processing begins so Hibernate scopes every read and write to the correct tenant.
 *
 * <p>When the room metadata carries no resolvable tenant, no tenant-scoped work is performed and the
 * anomaly is recorded, rather than writing to the default tenant.
 */
@Service
public class HandleLiveKitWebhookApplicationService implements HandleLiveKitWebhookUseCase {

    private static final Logger log =
            LoggerFactory.getLogger(HandleLiveKitWebhookApplicationService.class);

    private final LiveKitWebhookProcessingApplicationService processingService;

    public HandleLiveKitWebhookApplicationService(
            LiveKitWebhookProcessingApplicationService processingService) {
        this.processingService = processingService;
    }

    @Override
    public void handle(LiveKitWebhookEvent event) {
        Optional<String> tenant = event.tenantMetadata();
        if (tenant.isEmpty()) {
            log.warn(
                    "LiveKit webhook {} (event={}) carries no resolvable tenant metadata;"
                            + " skipping tenant-scoped processing",
                    event.webhookId(),
                    event.eventType());
            return;
        }
        try {
            TenantContext.setCurrentTenant(tenant.get());
            processingService.process(event);
        } finally {
            TenantContext.clear();
        }
    }
}
