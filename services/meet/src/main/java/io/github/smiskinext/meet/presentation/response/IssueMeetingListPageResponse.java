package io.github.smiskinext.meet.presentation.response;

import io.github.smiskinext.meet.application.result.ListIssueMeetingsResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Bespoke offset-paginated response envelope for issue-scoped meeting listings.
 *
 * <p>Carries a total count so the panel can render page numbers. This is intentionally separate
 * from the shared {@code PageResponse}/{@code PageMeta} to avoid rippling a {@code total} field
 * into every service's OpenAPI spec.
 */
@Schema(name = "IssueMeetingListPage", description = "Offset-paginated list of issue meetings")
public record IssueMeetingListPageResponse(
        @Schema(description = "Meeting summaries for the current page")
        List<MeetingSummaryResponse> data,

        @Schema(description = "Pagination metadata with total count")
        Meta meta) {

    public static IssueMeetingListPageResponse from(ListIssueMeetingsResult result) {
        List<MeetingSummaryResponse> items =
                result.items().stream().map(MeetingSummaryResponse::from).toList();
        Meta meta = new Meta(result.total(), result.offset(), result.pageSize(), result.hasNext());
        return new IssueMeetingListPageResponse(items, meta);
    }

    @Schema(
            name = "IssueMeetingListPageMeta",
            description = "Offset pagination metadata with total")
    public record Meta(
            @Schema(description = "Total non-deleted meetings linked to this issue")
            long total,

            @Schema(description = "Zero-based row offset used for this page")
            int offset,

            @Schema(description = "Maximum items per page requested")
            int pageSize,

            @Schema(description = "Whether more results exist after this page")
            boolean hasNext) {}
}
