package io.github.smiskinext.shared.infrastructure.web;

import java.util.List;

/**
 * Canonical JSON envelope for list/collection endpoints.
 *
 * <p>Successful single-resource responses return the resource directly with no envelope. List
 * endpoints instead wrap their items so pagination metadata travels alongside the data without
 * polluting item schemas:
 *
 * <pre>{@code
 * {
 *   "data": [ { ... }, { ... } ],
 *   "meta": { "size": 20, "hasNext": true, "nextPageToken": "eyJ..." }
 * }
 * }</pre>
 *
 * <p>Because the payload uses a concrete item type ({@code PageResponse<MeetingResponse>}) rather
 * than a generic status wrapper, springdoc generates a clean, dedicated schema per endpoint with no
 * post-processing required.
 *
 * @param <T> the item type carried in {@code data}
 */
public record PageResponse<T>(List<T> data, PageMeta meta) {

    public PageResponse {
        data = List.copyOf(data);
    }

    /** Wraps items with offset-pagination metadata. */
    public static <T> PageResponse<T> offset(List<T> items, int offset, Integer nextOffset) {
        return new PageResponse<>(items, PageMeta.offset(items.size(), offset, nextOffset));
    }

    /** Wraps items with cursor-pagination (keyset) metadata. */
    public static <T> PageResponse<T> cursor(List<T> items, String nextPageToken) {
        return new PageResponse<>(items, PageMeta.cursor(items.size(), nextPageToken));
    }
}
