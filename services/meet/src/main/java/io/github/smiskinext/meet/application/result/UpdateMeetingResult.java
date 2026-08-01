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
        Instant startTime,
        Instant endTime,
        String zoneId,
        String organizerEmail,
        String organizerDisplayName,
        String calendarUid,
        int calendarSequence,
        Instant createdAt) {

    public record IssueLink(String issueId, String issueKey, String projectKey) {}
}
