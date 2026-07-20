package io.github.smiskinext.meet.application.result;

import java.time.Instant;
import java.util.UUID;

/**
 * Result of a successful instant meeting creation.
 */
public record CreateInstantMeetingResult(
        UUID meetingId,
        String hostId,
        String shortCode,
        String type,
        String status,
        String title,
        String description,
        IssueLink issueLink,
        Settings settings,
        String zoneId,
        String organizerEmail,
        String organizerDisplayName,
        Instant createdAt,
        LiveKit livekit) {

    public record IssueLink(String issueId, String issueKey, String projectKey) {}

    public record Settings(
            String admissionPolicy,
            int maxParticipants,
            boolean allowScreenShare,
            boolean chatEnabled,
            boolean allowMicrophone,
            boolean allowVideo) {}

    public record LiveKit(String token, String roomName) {}
}
