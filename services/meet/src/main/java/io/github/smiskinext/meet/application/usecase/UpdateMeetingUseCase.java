package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.UpdateMeetingCommand;
import io.github.smiskinext.meet.application.result.UpdateMeetingResult;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.shared.application.UseCase;

public interface UpdateMeetingUseCase
        extends UseCase<UpdateMeetingCommand, UpdateMeetingResult, MeetingError> {}
