package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.UpdateMeetingInviteesCommand;
import io.github.smiskinext.meet.application.result.UpdateMeetingInviteesResult;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.shared.application.UseCase;

public interface UpdateMeetingInviteesUseCase
        extends UseCase<UpdateMeetingInviteesCommand, UpdateMeetingInviteesResult, MeetingError> {}
