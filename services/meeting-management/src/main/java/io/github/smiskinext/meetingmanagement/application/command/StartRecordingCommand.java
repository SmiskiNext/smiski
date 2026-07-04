package io.github.smiskinext.meetingmanagement.application.command;

import java.util.UUID;

public record StartRecordingCommand(UUID meetingId, UUID requesterId) {}
