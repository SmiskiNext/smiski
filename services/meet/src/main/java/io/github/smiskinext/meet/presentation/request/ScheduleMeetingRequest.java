package io.github.smiskinext.meet.presentation.request;

import io.github.smiskinext.meet.application.command.ScheduleMeetingCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

@Schema(description = "Request body for creating a scheduled meeting")
public record ScheduleMeetingRequest(
        @Schema(description = "Meeting title", example = "Sprint Planning") @NotBlank String title,

        @Schema(description = "Meeting description", example = "Weekly sprint planning session")
        @NotBlank String description,

        @NotNull @Valid IssueLink issueLink,
        @NotNull @Valid Settings settings,
        @NotNull @Valid TimeRange timeRange,
        @Schema(nullable = true) @Valid @Nullable List<@Valid Invitee> invitees) {

    @Schema(description = "Jira issue link")
    public record IssueLink(
            @NotBlank String issueId,
            @NotBlank String issueKey,
            @NotBlank String projectKey) {}

    @Schema(description = "Meeting room settings")
    public record Settings(
            @NotBlank String admissionPolicy,
            @Min(2) @Max(100) int maxParticipants,
            boolean allowScreenShare,
            boolean chatEnabled,
            boolean allowMicrophone,
            boolean allowVideo) {}

    @Schema(description = "Scheduled time range")
    public record TimeRange(
            @NotNull Instant startTime, @NotNull Instant endTime) {}

    @Schema(description = "Meeting invitee")
    public record Invitee(
            @NotBlank @Email String email,
            @NotBlank String accountId,
            @NotBlank String displayName) {}

    public ScheduleMeetingCommand toCommand(String accountId, String tenantId) {
        List<ScheduleMeetingCommand.Invitee> inviteeCommands = invitees != null
                ? invitees.stream()
                        .map(inv -> new ScheduleMeetingCommand.Invitee(
                                inv.email(), inv.accountId(), inv.displayName()))
                        .toList()
                : List.of();

        ScheduleMeetingCommand.IssueLink issueLinkCmd = new ScheduleMeetingCommand.IssueLink(
                issueLink.issueId(), issueLink.issueKey(), issueLink.projectKey());

        ScheduleMeetingCommand.Settings settingsCmd = new ScheduleMeetingCommand.Settings(
                settings.admissionPolicy(),
                settings.maxParticipants(),
                settings.allowScreenShare(),
                settings.chatEnabled(),
                settings.allowMicrophone(),
                settings.allowVideo());

        ScheduleMeetingCommand.TimeRange timeRangeCmd =
                new ScheduleMeetingCommand.TimeRange(timeRange.startTime(), timeRange.endTime());

        return new ScheduleMeetingCommand(
                tenantId,
                title,
                description,
                issueLinkCmd,
                settingsCmd,
                accountId,
                timeRangeCmd,
                inviteeCommands);
    }
}
