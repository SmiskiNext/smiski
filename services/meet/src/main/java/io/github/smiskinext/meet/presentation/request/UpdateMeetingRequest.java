package io.github.smiskinext.meet.presentation.request;

import io.github.smiskinext.meet.application.command.UpdateMeetingCommand;
import io.github.smiskinext.meet.presentation.validation.IanaZoneId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(description = "Full meeting update request")
public record UpdateMeetingRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String description,
        @NotNull @Valid IssueLink issueLink,
        @NotNull @Valid Settings settings,
        @NotBlank @IanaZoneId String zoneId,
        @Nullable @Valid TimeRange timeRange) {

    public record IssueLink(
            @NotBlank @Size(max = 64) String issueId,
            @NotBlank @Size(max = 64) String issueKey,
            @NotBlank @Size(max = 64) String projectKey) {}

    public record Settings(
            @NotBlank @Pattern(regexp = "ALLOW_ALL|MANUAL_APPROVAL") String admissionPolicy,

            @Min(2) @Max(100) int maxParticipants,
            boolean allowScreenShare,
            boolean chatEnabled,
            boolean allowMicrophone,
            boolean allowVideo) {}

    public record TimeRange(
            @NotNull Instant startTime, @NotNull Instant endTime) {}

    @AssertTrue(message = "timeRange startTime must be before endTime") public boolean isTimeRangeValid() {
        return timeRange == null
                || timeRange.startTime() == null
                || timeRange.endTime() == null
                || timeRange.startTime().isBefore(timeRange.endTime());
    }

    public UpdateMeetingCommand toCommand(UUID meetingId, String accountId, String tenantId) {
        return new UpdateMeetingCommand(
                meetingId,
                tenantId,
                accountId,
                title,
                description,
                new UpdateMeetingCommand.IssueLink(
                        issueLink.issueId(), issueLink.issueKey(), issueLink.projectKey()),
                new UpdateMeetingCommand.Settings(
                        settings.admissionPolicy(),
                        settings.maxParticipants(),
                        settings.allowScreenShare(),
                        settings.chatEnabled(),
                        settings.allowMicrophone(),
                        settings.allowVideo()),
                zoneId,
                timeRange == null
                        ? null
                        : new UpdateMeetingCommand.TimeRange(
                                timeRange.startTime(), timeRange.endTime()));
    }
}
