package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.query.ListMeetingsQuery;
import io.github.smiskinext.meet.application.result.ListMeetingsResult;
import io.github.smiskinext.meet.domain.ListMeetingsError;
import io.github.smiskinext.shared.application.UseCase;

/**
 * Inbound port for listing tenant meetings with filters and keyset pagination.
 */
public interface ListMeetingsUseCase
        extends UseCase<ListMeetingsQuery, ListMeetingsResult, ListMeetingsError> {}
