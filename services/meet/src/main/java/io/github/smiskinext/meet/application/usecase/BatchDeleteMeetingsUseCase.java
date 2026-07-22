package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.BatchDeleteMeetingsCommand;
import io.github.smiskinext.meet.application.result.BatchDeleteMeetingsResult;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.shared.application.UseCase;

public interface BatchDeleteMeetingsUseCase
        extends UseCase<BatchDeleteMeetingsCommand, BatchDeleteMeetingsResult, MeetingError> {}
