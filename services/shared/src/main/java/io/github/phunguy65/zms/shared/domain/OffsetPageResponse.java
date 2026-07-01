package io.github.phunguy65.zms.shared.domain;

import java.util.List;

/** A page of offset-based results with a flag indicating whether more pages exist. */
public record OffsetPageResponse<T>(List<T> items, int pageSize, int offset, boolean hasNext) {

    public OffsetPageResponse {
        items = List.copyOf(items);
    }

    public static <T> OffsetPageResponse<T> of(
            List<T> items, int pageSize, int offset, boolean hasNext) {
        return new OffsetPageResponse<>(items, pageSize, offset, hasNext);
    }

    public static <T> OffsetPageResponse<T> empty(int pageSize, int offset) {
        return new OffsetPageResponse<>(List.of(), pageSize, offset, false);
    }
}
