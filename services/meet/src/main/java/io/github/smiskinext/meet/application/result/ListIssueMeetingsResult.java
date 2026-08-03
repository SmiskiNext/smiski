package io.github.smiskinext.meet.application.result;

import java.util.List;

/**
 * Result of listing meetings linked to a single Jira issue with offset pagination.
 *
 * <p>Reuses {@link ListMeetingsResult.Item} for the individual meeting summaries. Carries the full
 * pagination metadata needed by the presentation layer's bespoke envelope.
 *
 * @param items    the page of meeting summaries in order
 * @param total    total number of non-deleted meetings linked to the issue across all pages
 * @param offset   the zero-based row offset used for this page
 * @param pageSize the maximum items per page requested
 * @param hasNext  whether more results exist after this page
 */
public record ListIssueMeetingsResult(
        List<ListMeetingsResult.Item> items,
        long total,
        int offset,
        int pageSize,
        boolean hasNext) {

    public ListIssueMeetingsResult {
        items = List.copyOf(items);
    }
}
