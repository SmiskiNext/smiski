package io.github.smiskinext.meet.infrastructure.persistence;

/**
 * Persistence DTO for {@code MeetingSettings} stored as JSONB.
 *
 * <p>This is a pure data holder used exclusively by the persistence layer.
 * It carries no domain logic and no API serialization concerns.
 * Mapped via {@code @JdbcTypeCode(SqlTypes.JSON)} on {@link MeetingJpaEntity}.
 */
public record MeetingSettingsJson(
        String admissionPolicy,
        int maxParticipants,
        boolean allowScreenShare,
        boolean chatEnabled,
        boolean allowMicrophone,
        boolean allowVideo) {}
