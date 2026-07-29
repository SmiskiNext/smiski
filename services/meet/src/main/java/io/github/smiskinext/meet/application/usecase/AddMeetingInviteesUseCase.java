package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.AddMeetingInviteesCommand;
import io.github.smiskinext.meet.application.result.AddMeetingInviteesResult;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.shared.application.UseCase;

public interface AddMeetingInviteesUseCase
        extends UseCase<AddMeetingInviteesCommand, AddMeetingInviteesResult, MeetingError> {}
