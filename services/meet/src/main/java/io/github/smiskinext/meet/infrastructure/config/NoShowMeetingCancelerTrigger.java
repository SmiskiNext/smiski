package io.github.smiskinext.meet.infrastructure.config;

import io.github.smiskinext.meet.application.service.NoShowMeetingCancelerApplicationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class NoShowMeetingCancelerTrigger {

    private static final Logger logger =
            LoggerFactory.getLogger(NoShowMeetingCancelerTrigger.class);

    private final NoShowMeetingCancelerApplicationService canceler;
    private final int batchSize;

    public NoShowMeetingCancelerTrigger(
            NoShowMeetingCancelerApplicationService canceler,
            @Value("${smiski.meet.no-show-canceler.batch-size:100}") int batchSize) {
        this.canceler = canceler;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${smiski.meet.no-show-canceler.fixed-delay:PT5M}")
    public void cancelExpiredMeetings() {
        logger.debug("No-show canceler triggered (batch size: {})", batchSize);
        canceler.cancelAllExpired(batchSize);
    }
}
