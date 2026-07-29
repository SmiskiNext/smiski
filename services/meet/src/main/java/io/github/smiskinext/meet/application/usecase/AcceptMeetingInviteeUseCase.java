package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.AcceptMeetingInviteeCommand;
import io.github.smiskinext.meet.application.result.AcceptMeetingInviteeResult;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.shared.application.UseCase;

public interface AcceptMeetingInviteeUseCase
        extends UseCase<AcceptMeetingInviteeCommand, AcceptMeetingInviteeResult, MeetingError> {}
