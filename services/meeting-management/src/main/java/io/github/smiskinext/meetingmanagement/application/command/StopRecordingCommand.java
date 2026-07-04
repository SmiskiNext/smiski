package io.github.smiskinext.meetingmanagement.application.command;

import java.util.UUID;

public record StopRecordingCommand(UUID meetingId, UUID requesterId) {}
