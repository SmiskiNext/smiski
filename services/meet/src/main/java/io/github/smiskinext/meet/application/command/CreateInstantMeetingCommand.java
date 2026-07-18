package io.github.smiskinext.meet.application.command;

import io.github.smiskinext.shared.application.Command;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Command to create an instant meeting.
 */
public record CreateInstantMeetingCommand(
        String tenantId,
        String title,
        String description,
        IssueLink issueLink,
        Settings settings,
        Host host,
        String zoneId,
        List<Invitee> invitees)
        implements Command {

    public record IssueLink(String issueId, String issueKey, String projectKey) {}

    public record Settings(
            String admissionPolicy,
            int maxParticipants,
            boolean allowScreenShare,
            boolean chatEnabled,
            boolean allowMicrophone,
            boolean allowVideo) {}

    public record Host(
            String accountId,
            String displayName,
            String deviceId,
            @Nullable String avatarUrl) {}

    public record Invitee(String email, String accountId, String displayName) {}
}
