package io.github.smiskinext.meet.infrastructure.persistence;

import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Infrastructure-local carrier for the meeting detail constructor projection.
 *
 * <p>Holds {@code type} and {@code status} as raw {@code String} columns because JPQL constructor
 * expressions cannot convert a {@code String} column into a domain enum. The adapter converts these
 * into the enum-typed {@link io.github.smiskinext.meet.domain.projection.MeetingDetail}.
 */
record MeetingDetailProjection(
        UUID id,
        String hostId,
        String shortCode,
        String type,
        String status,
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
