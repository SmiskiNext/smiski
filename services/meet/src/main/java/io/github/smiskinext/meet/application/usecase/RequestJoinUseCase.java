package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.RequestJoinCommand;
import io.github.smiskinext.meet.application.result.RequestJoinResult;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.shared.application.UseCase;

public interface RequestJoinUseCase
        extends UseCase<RequestJoinCommand, RequestJoinResult, MeetingError> {}
