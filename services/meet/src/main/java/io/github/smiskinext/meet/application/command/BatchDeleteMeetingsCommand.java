package io.github.smiskinext.meet.application.command;

import io.github.smiskinext.shared.application.Command;

import java.util.List;
import java.util.UUID;

public record BatchDeleteMeetingsCommand(List<UUID> meetingIds, String tenantId, String accountId)
        implements Command {}
