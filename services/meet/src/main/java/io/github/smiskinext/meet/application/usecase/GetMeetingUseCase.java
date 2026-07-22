package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.query.GetMeetingQuery;
import io.github.smiskinext.meet.application.result.GetMeetingResult;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.shared.application.UseCase;

public interface GetMeetingUseCase
        extends UseCase<GetMeetingQuery, GetMeetingResult, MeetingError> {}
