package io.github.smiskinext.meet.application.result;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Snapshot of a meeting captured at the moment it was canceled.
 *
 * <p>Framework-agnostic: no Jackson or OpenAPI annotations. The presentation layer maps this into
 * the cancel response DTO.
 */
public record CancelMeetingResult(
        UUID meetingId,
        String hostId,
        String shortCode,
        String type,
        String status,
        @Nullable String cancelReason,
        String title,
        String description,
        IssueLink issueLink,
        Settings settings,
        @Nullable Instant startTime,
        @Nullable Instant endTime,
        String zoneId,
        String organizerEmail,
        String organizerDisplayName,
        String calendarUid,
        int calendarSequence,
        Instant createdAt) {

    public record IssueLink(String issueId, String issueKey, String projectKey) {}

    public record Settings(
            String admissionPolicy,
            int maxParticipants,
            boolean allowScreenShare,
            boolean chatEnabled,
            boolean allowMicrophone,
            boolean allowVideo) {}
}
