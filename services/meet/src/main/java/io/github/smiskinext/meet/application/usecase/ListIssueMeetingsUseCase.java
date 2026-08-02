package io.github.smiskinext.meet.application.usecase;

import io.github.smiskinext.meet.application.query.ListIssueMeetingsQuery;
import io.github.smiskinext.meet.application.result.ListIssueMeetingsResult;
import io.github.smiskinext.meet.domain.ListMeetingsError;
import io.github.smiskinext.shared.application.UseCase;

/**
 * Inbound port for listing meetings linked to a specific Jira issue with offset pagination.
 */
public interface ListIssueMeetingsUseCase
        extends UseCase<ListIssueMeetingsQuery, ListIssueMeetingsResult, ListMeetingsError> {}
