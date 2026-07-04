package io.github.smiskinext.meetingmanagement.presentation.request;

import io.github.smiskinext.meetingmanagement.application.command.ScheduleMeetingCommand;
import io.github.smiskinext.meetingmanagement.application.command.ScheduleMeetingCommand.InviteeInput;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTimeRange;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ScheduleMeetingRequest(
        @Nullable @Size(max = 255) String title,
        @Nullable @Size(max = 2000) String description,
        @NotNull @Future Instant startTime,
        @NotNull @Future Instant endTime,
        @NotNull @Valid MeetingSettingsRequest settings,
        @Nullable @Size(max = 100) @Valid List<InviteeRequest> invitees) {

    public ScheduleMeetingCommand toCommand(UUID hostId) {
        MeetingTitle meetingTitle = title != null ? MeetingTitle.of(title) : null;
        MeetingTimeRange timeRange = MeetingTimeRange.of(startTime, endTime);
        List<InviteeInput> inviteeInputs = invitees != null
                ? invitees.stream()
                        .distinct()
                        .map(i -> new InviteeInput(i.email()))
                        .toList()
                : List.of();
        return new ScheduleMeetingCommand(
                hostId,
                meetingTitle,
                description,
                timeRange,
                settings.toDomain(),
                inviteeInputs,
                settings.password());
    }

    public record InviteeRequest(@NotBlank @Email String email) {}
}
