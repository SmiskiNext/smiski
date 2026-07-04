package io.github.smiskinext.meetingmanagement.presentation.request;

import io.github.smiskinext.meetingmanagement.application.command.CreateInstantMeetingCommand;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record CreateInstantMeetingRequest(
        @Nullable String title, @NotNull @Valid MeetingSettingsRequest settings) {

    public CreateInstantMeetingCommand toCommand(UUID hostId) {
        MeetingTitle meetingTitle = title != null ? MeetingTitle.of(title) : null;
        return new CreateInstantMeetingCommand(
                hostId, meetingTitle, settings.toDomain(), settings.password());
    }
}
