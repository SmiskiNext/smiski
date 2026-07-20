package io.github.smiskinext.meet.application.result;

import java.time.Instant;
import java.util.UUID;

public record UpdateMeetingResult(
        UUID meetingId,
        String hostId,
        String shortCode,
        String type,
        String status,
        String title,
        String description,
        IssueLink issueLink,
        Settings settings,
        Instant startTime,
        Instant endTime,
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
