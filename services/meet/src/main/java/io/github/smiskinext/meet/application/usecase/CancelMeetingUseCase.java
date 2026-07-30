package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.CancelMeetingCommand;
import io.github.smiskinext.meet.application.result.CancelMeetingResult;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.shared.application.UseCase;

public interface CancelMeetingUseCase
        extends UseCase<CancelMeetingCommand, CancelMeetingResult, MeetingError> {}
