package io.github.smiskinext.meet.domain.projection;

/**
 * Read-only projection of meeting settings for summaries.
 *
 * <p>Simplified field set matching the refactored {@code MeetingSettings}.
 */
public record MeetingSettingsSummary(
        String admissionPolicy,
        boolean allowGuest,
        int maxParticipants,
        boolean allowScreenShare,
        boolean chatEnabled,
        boolean allowMicrophone,
        boolean allowVideo) {}
