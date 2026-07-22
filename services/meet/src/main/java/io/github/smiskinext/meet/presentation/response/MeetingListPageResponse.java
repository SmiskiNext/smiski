package io.github.smiskinext.meet.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * OpenAPI schema describing a keyset-paginated page of tenant meetings.
 */
@Schema(name = "MeetingListPage", description = "Paginated list of tenant meetings")
public record MeetingListPageResponse(List<MeetingSummaryResponse> data, Meta meta) {

    @Schema(name = "MeetingListPageMeta")
    public record Meta(int size, boolean hasNext, @Nullable String nextPageToken) {}
}
