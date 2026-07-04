package io.github.smiskinext.meetingmanagement.application.command;

import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record CreateInstantMeetingCommand(
        UUID hostId,
        @Nullable MeetingTitle title,
        MeetingSettings settings,
        @Nullable String rawPassword) {}
