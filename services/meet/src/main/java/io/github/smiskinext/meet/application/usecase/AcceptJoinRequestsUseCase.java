package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.AcceptJoinRequestsCommand;
import io.github.smiskinext.meet.application.result.AcceptJoinRequestsResult;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.shared.application.UseCase;

public interface AcceptJoinRequestsUseCase
        extends UseCase<AcceptJoinRequestsCommand, AcceptJoinRequestsResult, MeetingError> {}
