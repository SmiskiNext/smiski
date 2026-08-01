package io.github.smiskinext.meet.application.query;

import io.github.smiskinext.shared.application.Query;

import java.util.UUID;

/**
 * Read-intent input for retrieving a paginated list of PENDING join requests for a meeting.
 *
 * @param meetingId the identifier of the meeting whose pending queue is requested
 * @param tenantId  the resolved tenant identifier enforcing tenant isolation
 * @param accountId the resolved caller account identifier (must be the meeting host)
 * @param offset    zero-based start index for the result page
 * @param pageSize  maximum number of items to return per page
 */
public record ListPendingJoinRequestsQuery(
        UUID meetingId, String tenantId, String accountId, int offset, int pageSize)
        implements Query {}
