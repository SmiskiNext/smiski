package io.github.smiskinext.meetingmanagement.application.command;

import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Command for fully replacing meeting settings via PUT. */
public record PutMeetingSettingsCommand(
        UUID meetingId,
        UUID requesterId,
        MeetingSettings settings,
        @Nullable String rawPassword) {}
