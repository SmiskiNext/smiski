package io.github.smiskinext.meetingmanagement.infrastructure.jobs;

import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import io.github.smiskinext.meetingmanagement.domain.port.RecordingRepository;
import io.github.smiskinext.meetingmanagement.infrastructure.config.LiveKitProperties;
import io.github.phunguy65.zms.shared.domain.Result;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RecordingCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RecordingCleanupJob.class);
    private static final String TIMEOUT_MESSAGE =
            "Timed out waiting for LiveKit egress_started webhook";

    private final RecordingRepository recordingRepository;
    private final LiveKitProperties liveKitProperties;
    private final ApplicationEventPublisher eventPublisher;

    public RecordingCleanupJob(
            RecordingRepository recordingRepository,
            LiveKitProperties liveKitProperties,
            ApplicationEventPublisher eventPublisher) {
        this.recordingRepository = recordingRepository;
        this.liveKitProperties = liveKitProperties;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void failStalePendingRecordings() {
        Instant cutoff = Instant.now().minus(liveKitProperties.getRecording().getPendingMaxAge());
        var staleRecordings = recordingRepository.findPendingCreatedBefore(cutoff);
        int failedCount = 0;

        for (var recording : staleRecordings) {
            var failResult = recording.fail(TIMEOUT_MESSAGE);
            if (failResult instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
                log.debug(
                        "Skipping stale recording '{}' cleanup because state changed: {}",
                        recording.getId().value(),
                        error.message());
                continue;
            }

            var saved = recordingRepository.save(recording);
            saved.getDomainEvents().stream()
                    .filter(e -> e instanceof PublishableEvent)
                    .map(e -> (PublishableEvent) e)
                    .forEach(eventPublisher::publishEvent);
            saved.clearDomainEvents();
            failedCount++;
        }

        if (failedCount > 0) {
            log.info("Failed {} stale pending recordings", failedCount);
        }
    }
}
