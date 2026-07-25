package io.github.smiskinext.meet.application.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Result of retrieving a single meeting together with its invitees and joined participants.
 *
 * <p>Framework-agnostic snapshot mirroring the meeting detail fields; the tenant identifier is
 * intentionally excluded so it is never leaked to callers.
 */
public record GetMeetingResult(
        Meeting meeting, List<Invitee> invitees, List<Participant> participants) {

    public record Meeting(
            UUID id,
            String hostId,
            String shortCode,
            String type,
            String status,
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
            Instant createdAt) {}

    public record IssueLink(String issueId, String issueKey, String projectKey) {}

    public record Settings(
            String admissionPolicy,
            int maxParticipants,
            boolean allowScreenShare,
            boolean chatEnabled,
            boolean allowMicrophone,
            boolean allowVideo) {}

    public record Invitee(
            String accountId,
            String email,
            String displayName,
            String status,
            Instant invitedAt,
            @Nullable Instant respondedAt) {}

    public record Participant(
            String accountId,
            String role,
            Instant joinedAt,
            @Nullable Instant leftAt) {}
}
