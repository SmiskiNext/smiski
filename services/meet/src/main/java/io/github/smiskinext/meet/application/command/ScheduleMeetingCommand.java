package io.github.smiskinext.meet.application.command;

import io.github.smiskinext.shared.application.Command;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Command to create a scheduled meeting.
 */
public record ScheduleMeetingCommand(
        String tenantId,
        String title,
        String description,
        IssueLink issueLink,
        Settings settings,
        String hostAccountId,
        String organizerEmail,
        String organizerDisplayName,
        TimeRange timeRange,
        String zoneId,
        @Nullable List<Invitee> invitees)
        implements Command {

    public record IssueLink(String issueId, String issueKey, String projectKey) {}

    public record Settings(
            String admissionPolicy,
            int maxParticipants,
            boolean allowScreenShare,
            boolean chatEnabled,
            boolean allowMicrophone,
            boolean allowVideo) {}

    public record TimeRange(Instant start, Instant end) {}

    public record Invitee(String email, String accountId, String displayName) {}
}
