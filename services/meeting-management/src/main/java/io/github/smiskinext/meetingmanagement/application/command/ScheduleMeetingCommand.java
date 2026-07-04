package io.github.smiskinext.meetingmanagement.application.command;

import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTimeRange;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ScheduleMeetingCommand(
        UUID hostId,
        @Nullable MeetingTitle title,
        @Nullable String description,
        MeetingTimeRange timeRange,
        MeetingSettings settings,
        List<InviteeInput> invitees,
        @Nullable String rawPassword) {

    /**
     * An invitee identified by their email address.
     */
    public record InviteeInput(String email) {}
}
