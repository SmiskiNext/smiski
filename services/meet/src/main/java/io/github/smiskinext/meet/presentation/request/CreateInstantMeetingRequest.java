package io.github.smiskinext.meet.presentation.request;

import io.github.smiskinext.meet.application.command.CreateInstantMeetingCommand;
import io.github.smiskinext.meet.presentation.validation.IanaZoneId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.jspecify.annotations.Nullable;

@Schema(description = "Request body for creating an instant meeting")
public record CreateInstantMeetingRequest(
        @Schema(description = "Meeting title", example = "Sprint Planning") @NotBlank String title,

        @Schema(description = "Meeting description", example = "Daily standup for the team")
        @NotBlank String description,

        @NotNull @Valid IssueLink issueLink,
        @NotNull @Valid Settings settings,
        @NotNull @Valid Host host,

        @Schema(description = "Organizer email address", example = "alice@example.com")
        @NotBlank @Email @Size(max = 255) String organizerEmail,

        @Schema(description = "Organizer display name", example = "Alice Nguyen")
        @NotBlank @Size(max = 255) String organizerDisplayName,

        @Schema(description = "Host IANA time zone id", example = "Asia/Ho_Chi_Minh")
        @NotBlank @IanaZoneId
        String zoneId,

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

    @Schema(description = "Host participant info")
    public record Host(
            @NotBlank String displayName,
            @NotBlank String deviceId,

            @Schema(description = "Host avatar URL", nullable = true) @Nullable String avatarUrl) {}

    @Schema(description = "Meeting invitee")
    public record Invitee(
            @NotBlank @Email String email,
            @NotBlank String accountId,
            @NotBlank String displayName) {}

    public CreateInstantMeetingCommand toCommand(String accountId, String tenantId) {
        List<CreateInstantMeetingCommand.Invitee> inviteeCommands = invitees != null
                ? invitees.stream()
                        .map(inv -> new CreateInstantMeetingCommand.Invitee(
                                inv.email(), inv.accountId(), inv.displayName()))
                        .toList()
                : List.of();

        CreateInstantMeetingCommand.IssueLink issueLinkCmd =
                new CreateInstantMeetingCommand.IssueLink(
                        issueLink.issueId(), issueLink.issueKey(), issueLink.projectKey());

        CreateInstantMeetingCommand.Settings settingsCmd = new CreateInstantMeetingCommand.Settings(
                settings.admissionPolicy(),
                settings.maxParticipants(),
                settings.allowScreenShare(),
                settings.chatEnabled(),
                settings.allowMicrophone(),
                settings.allowVideo());

        CreateInstantMeetingCommand.Host hostCmd = new CreateInstantMeetingCommand.Host(
                accountId, host.displayName(), host.deviceId(), host.avatarUrl());

        return new CreateInstantMeetingCommand(
                tenantId,
                title,
                description,
                issueLinkCmd,
                settingsCmd,
                hostCmd,
                organizerEmail,
                organizerDisplayName,
                zoneId,
                inviteeCommands);
    }
}
