package io.github.smiskinext.meetingmanagement.application.command;

import java.util.UUID;

public record EndMeetingCommand(UUID meetingId, UUID requesterId) {}
