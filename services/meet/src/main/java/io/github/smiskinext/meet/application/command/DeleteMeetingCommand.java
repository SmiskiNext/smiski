package io.github.smiskinext.meet.application.command;

import io.github.smiskinext.shared.application.Command;

import java.util.UUID;

public record DeleteMeetingCommand(UUID meetingId, String tenantId, String accountId)
        implements Command {}
