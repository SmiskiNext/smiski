package io.github.smiskinext.meet.presentation.response;

import io.github.smiskinext.meet.application.result.ListPendingJoinRequestsResult;
import io.github.smiskinext.meet.domain.projection.JoinRequestSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Response for listing PENDING join requests for a meeting host.
 *
 * <p>Contains a page of request summaries and pagination metadata for offset-based navigation.
 */
@Schema(description = "Paginated list of PENDING join requests for the meeting host")
public record ListPendingJoinRequestsResponse(
        @Schema(description = "Pending join request summaries for the current page")
        List<Item> results,

        @Schema(description = "Pagination metadata") Meta meta) {

    public static ListPendingJoinRequestsResponse from(ListPendingJoinRequestsResult result) {
        List<Item> items = result.page().items().stream().map(Item::from).toList();
        Meta meta = new Meta(
                (int) result.total(), result.page().offset(), result.page().pageSize());
        return new ListPendingJoinRequestsResponse(items, meta);
    }

    @Schema(description = "Summary of a single pending join request")
    public record Item(
            @Schema(description = "Unique identifier of the join request")
            String requestId,

            @Schema(description = "Account identifier of the waiting participant")
            String accountId,

            @Schema(description = "Display name submitted by the waiting participant")
            String displayName,

            @Schema(
                    description = "Request status; always PENDING in this list",
                    example = "PENDING")
            String status,

            @Schema(description = "ISO-8601 instant when the request was created")
            Instant requestedAt,

            @Schema(description = "ISO-8601 instant when the request expires")
            Instant expiresAt) {

        public static Item from(JoinRequestSummary summary) {
            return new Item(
                    summary.id().toString(),
                    summary.accountId(),
                    summary.displayName(),
                    summary.status().name(),
                    summary.requestedAt(),
                    summary.expiresAt());
        }
    }

    @Schema(description = "Pagination metadata for the current page")
    public record Meta(
            @Schema(description = "Total number of PENDING requests across all pages")
            int total,

            @Schema(description = "Zero-based start index used for this page")
            int offset,

            @Schema(description = "Maximum items requested per page")
            int pageSize) {}
}
