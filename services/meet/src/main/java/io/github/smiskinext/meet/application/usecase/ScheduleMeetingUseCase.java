package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.ScheduleMeetingCommand;
import io.github.smiskinext.meet.application.result.ScheduleMeetingResult;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.shared.application.UseCase;

public interface ScheduleMeetingUseCase
        extends UseCase<ScheduleMeetingCommand, ScheduleMeetingResult, MeetingError> {}
