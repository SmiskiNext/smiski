package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.DeclineMeetingInviteeCommand;
import io.github.smiskinext.meet.application.result.DeclineMeetingInviteeResult;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.shared.application.UseCase;

public interface DeclineMeetingInviteeUseCase
        extends UseCase<DeclineMeetingInviteeCommand, DeclineMeetingInviteeResult, MeetingError> {}
