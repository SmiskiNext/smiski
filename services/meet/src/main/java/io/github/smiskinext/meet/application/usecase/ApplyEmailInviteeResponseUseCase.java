package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.ApplyEmailInviteeResponseCommand;

/**
 * Inbound port: apply an invitee response received via an inbound email reply (iMIP).
 *
 * <p>This use case does not return a meaningful result value and never throws on business-rule
 * violations (unknown meeting, non-invitee, invalid transition) — it silently ignores them so
 * the consumer partition is never blocked.
 */
public interface ApplyEmailInviteeResponseUseCase {

    void execute(ApplyEmailInviteeResponseCommand command);
}
