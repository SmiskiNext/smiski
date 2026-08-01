package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.query.ListPendingJoinRequestsQuery;
import io.github.smiskinext.meet.application.result.ListPendingJoinRequestsResult;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.shared.application.UseCase;

public interface ListPendingJoinRequestsUseCase
        extends UseCase<
                ListPendingJoinRequestsQuery, ListPendingJoinRequestsResult, MeetingError> {}
