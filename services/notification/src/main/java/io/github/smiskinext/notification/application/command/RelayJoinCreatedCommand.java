package io.github.smiskinext.notification.application.command;

import io.github.smiskinext.notification.domain.model.PendingJoinRequest;
import io.github.smiskinext.shared.application.Command;

import java.util.UUID;

/**
 * Command to relay a join-request-created event to connected meeting-host SSE streams.
 *
 * @param meetingId the meeting for which the join request was created
 * @param request   the pending join request to relay
 */
public record RelayJoinCreatedCommand(UUID meetingId, PendingJoinRequest request)
        implements Command {}
