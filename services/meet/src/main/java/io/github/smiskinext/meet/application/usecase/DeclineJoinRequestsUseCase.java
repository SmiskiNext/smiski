package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.DeclineJoinRequestsCommand;
import io.github.smiskinext.meet.application.result.DeclineJoinRequestsResult;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.shared.application.UseCase;

public interface DeclineJoinRequestsUseCase
        extends UseCase<DeclineJoinRequestsCommand, DeclineJoinRequestsResult, MeetingError> {}
