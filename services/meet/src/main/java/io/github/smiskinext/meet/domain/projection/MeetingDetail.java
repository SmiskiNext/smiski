package io.github.smiskinext.meet.domain.projection;

import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.MeetingType;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Read-only projection carrying the exact fields required to render a single meeting detail view.
 *
 * <p>Framework-agnostic snapshot fetched via constructor projection to avoid reconstituting the
 * full {@code Meeting} aggregate. The tenant identifier is intentionally excluded so it is never
 * leaked to callers.
 */
public record MeetingDetail(
        UUID id,
        String hostId,
        String shortCode,
        MeetingType type,
        MeetingStatus status,
        String title,
        String description,
        String issueId,
        String issueKey,
        String projectKey,
        MeetingSettings settings,
        @Nullable Instant startTime,
        @Nullable Instant endTime,
        String zoneId,
        String organizerEmail,
        String organizerDisplayName,
        String calendarUid,
        int calendarSequence,
        Instant createdAt) {}
