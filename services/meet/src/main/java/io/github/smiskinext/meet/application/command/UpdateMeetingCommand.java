package io.github.smiskinext.meet.application.command;

import io.github.smiskinext.shared.application.Command;

import java.time.Instant;
import java.util.UUID;

public record UpdateMeetingCommand(
        UUID meetingId,
        String tenantId,
        String accountId,
        String title,
        String description,
        IssueLink issueLink,
        Settings settings,
        String zoneId,
        TimeRange timeRange)
        implements Command {

    public record IssueLink(String issueId, String issueKey, String projectKey) {}

    public record Settings(
            String admissionPolicy,
            int maxParticipants,
            boolean allowScreenShare,
            boolean chatEnabled,
            boolean allowMicrophone,
            boolean allowVideo) {}

    public record TimeRange(Instant startTime, Instant endTime) {}
}
