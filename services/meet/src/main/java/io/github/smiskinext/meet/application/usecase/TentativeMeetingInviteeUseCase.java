package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.TentativeMeetingInviteeCommand;
import io.github.smiskinext.meet.application.result.TentativeMeetingInviteeResult;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.shared.application.UseCase;

public interface TentativeMeetingInviteeUseCase
        extends UseCase<
                TentativeMeetingInviteeCommand, TentativeMeetingInviteeResult, MeetingError> {}
