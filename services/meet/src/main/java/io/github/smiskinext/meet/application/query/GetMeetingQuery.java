package io.github.smiskinext.meet.application.query;

import io.github.smiskinext.shared.application.Query;

import java.util.UUID;

/**
 * Read-intent input for retrieving a single tenant-scoped meeting with its people.
 *
 * @param meetingId the identifier of the meeting to retrieve
 * @param tenantId  the resolved tenant identifier enforcing tenant isolation
 * @param accountId the resolved caller account identifier
 */
public record GetMeetingQuery(UUID meetingId, String tenantId, String accountId) implements Query {}
