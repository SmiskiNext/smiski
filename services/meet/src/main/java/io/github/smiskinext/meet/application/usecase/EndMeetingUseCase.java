package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.EndMeetingCommand;
import io.github.smiskinext.meet.application.result.EndMeetingResult;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.shared.application.UseCase;

public interface EndMeetingUseCase
        extends UseCase<EndMeetingCommand, EndMeetingResult, MeetingError> {}
