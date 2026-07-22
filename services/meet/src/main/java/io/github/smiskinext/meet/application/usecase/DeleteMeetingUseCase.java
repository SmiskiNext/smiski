package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.DeleteMeetingCommand;
import io.github.smiskinext.meet.application.result.DeleteMeetingResult;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.shared.application.UseCase;

public interface DeleteMeetingUseCase
        extends UseCase<DeleteMeetingCommand, DeleteMeetingResult, MeetingError> {}
