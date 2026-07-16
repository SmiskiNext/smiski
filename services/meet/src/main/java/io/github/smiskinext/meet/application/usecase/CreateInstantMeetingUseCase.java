package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.CreateInstantMeetingCommand;
import io.github.smiskinext.meet.application.result.CreateInstantMeetingResult;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.shared.application.UseCase;

public interface CreateInstantMeetingUseCase
        extends UseCase<CreateInstantMeetingCommand, CreateInstantMeetingResult, MeetingError> {}
