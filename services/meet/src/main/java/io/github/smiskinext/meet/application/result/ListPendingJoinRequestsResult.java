package io.github.smiskinext.meet.application.result;

import io.github.smiskinext.meet.domain.projection.JoinRequestSummary;
import io.github.smiskinext.shared.domain.OffsetPageResponse;

/**
 * Result of listing the PENDING join-request queue for a meeting.
 *
 * <p>Wraps the offset-paged projection returned by the repository together with the global
 * total count of PENDING requests across all pages, so the presentation layer can render
 * accurate pagination metadata without coupling to domain or infrastructure types.
 */
public record ListPendingJoinRequestsResult(
        OffsetPageResponse<JoinRequestSummary> page, long total) {}
