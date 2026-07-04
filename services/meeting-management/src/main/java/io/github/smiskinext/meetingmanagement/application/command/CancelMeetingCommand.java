package io.github.smiskinext.meetingmanagement.application.command;

import java.util.UUID;

public record CancelMeetingCommand(UUID meetingId, UUID requesterId) {}
