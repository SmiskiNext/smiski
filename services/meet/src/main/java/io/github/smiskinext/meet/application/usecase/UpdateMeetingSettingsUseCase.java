package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.UpdateMeetingSettingsCommand;
import io.github.smiskinext.meet.application.result.UpdateMeetingSettingsResult;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.shared.application.UseCase;

public interface UpdateMeetingSettingsUseCase
        extends UseCase<UpdateMeetingSettingsCommand, UpdateMeetingSettingsResult, MeetingError> {}
