package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.command.RemoveMeetingInviteesCommand;
import io.github.smiskinext.meet.application.result.RemoveMeetingInviteesResult;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.shared.application.UseCase;

public interface RemoveMeetingInviteesUseCase
        extends UseCase<RemoveMeetingInviteesCommand, RemoveMeetingInviteesResult, MeetingError> {}
