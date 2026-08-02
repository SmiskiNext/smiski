package io.github.smiskinext.notification.application.command;

import io.github.smiskinext.notification.domain.model.JoinDecision;
import io.github.smiskinext.shared.application.Command;

import java.util.UUID;

/**
 * Command to relay a join-request-resolved decision to connected requester SSE streams.
 *
 * @param requestId the join request whose decision was resolved
 * @param decision  the terminal decision to deliver
 */
public record RelayJoinResolvedCommand(UUID requestId, JoinDecision decision) implements Command {}
