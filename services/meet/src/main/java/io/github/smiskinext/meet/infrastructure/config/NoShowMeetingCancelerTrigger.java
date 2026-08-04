package io.github.smiskinext.meet.infrastructure.config;

import io.github.smiskinext.meet.application.service.NoShowMeetingCancelerApplicationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically cancels meetings nobody joined, driven by {@link NoShowCancelerProperties}.
 *
 * <p>{@code @Scheduled} resolves its delay from the property placeholder rather than the bound
 * record, so the placeholder key and {@code NoShowCancelerProperties} prefix must stay in sync.
 */
@Component
@EnableScheduling
public class NoShowMeetingCancelerTrigger {

    private static final Logger logger =
            LoggerFactory.getLogger(NoShowMeetingCancelerTrigger.class);

    private final NoShowMeetingCancelerApplicationService canceler;
    private final NoShowCancelerProperties properties;

    public NoShowMeetingCancelerTrigger(
            NoShowMeetingCancelerApplicationService canceler, NoShowCancelerProperties properties) {
        this.canceler = canceler;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.meet.no-show-canceler.fixed-delay:PT5M}")
    public void cancelExpiredMeetings() {
        logger.debug("No-show canceler triggered (batch size: {})", properties.batchSize());
        canceler.cancelAllExpired(properties.batchSize());
    }
}
