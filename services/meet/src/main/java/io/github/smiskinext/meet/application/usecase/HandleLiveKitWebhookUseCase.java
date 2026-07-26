package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.domain.model.valueobject.LiveKitWebhookEvent;

/**
 * Inbound port for processing a verified LiveKit webhook event against meeting and
 * participation-log state. Resolves the owning tenant from the event's room metadata, binds it for
 * the processing transaction, and drives idempotent meeting-lifecycle and participation-log
 * transitions.
 */
public interface HandleLiveKitWebhookUseCase {

    void handle(LiveKitWebhookEvent event);
}
