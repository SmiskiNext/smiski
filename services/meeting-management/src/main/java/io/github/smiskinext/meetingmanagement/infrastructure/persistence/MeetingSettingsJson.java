package io.github.smiskinext.meetingmanagement.infrastructure.persistence;

import org.jspecify.annotations.Nullable;

/**
 * Persistence DTO for {@code MeetingSettings} stored as JSONB.
 *
 * <p>This is a pure data holder used exclusively by the persistence layer.
 * It carries no domain logic and no API serialization concerns.
 * Mapped via {@code @JdbcTypeCode(SqlTypes.JSON)} on {@link MeetingJpaEntity}.
 */
public record MeetingSettingsJson(
        String admissionPolicy,
        boolean allowGuest,
        int maxParticipants,
        boolean allowScreenShare,
        boolean chatEnabled,
        boolean allowMicrophone,
        boolean allowVideo,
        @Nullable String password) {}
