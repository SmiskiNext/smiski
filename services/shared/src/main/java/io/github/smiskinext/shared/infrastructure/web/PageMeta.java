package io.github.smiskinext.shared.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

/**
 * Pagination metadata accompanying a {@link PageResponse} list payload.
 *
 * <p>Supports both pagination styles used across services; unused members are omitted from the JSON
 * ({@code NON_NULL}):
 *
 * <ul>
 *   <li><b>Offset</b> — populate {@code size}, {@code offset}, {@code nextOffset}; leave
 *       {@code nextPageToken} null.
 *   <li><b>Cursor (keyset)</b> — populate {@code size}, {@code nextPageToken}; leave
 *       {@code offset}/{@code nextOffset} null.
 * </ul>
 *
 * @param size          number of items in the current page
 * @param hasNext       whether more results exist after this page
 * @param offset        zero-based offset of the current page (offset pagination only)
 * @param nextOffset    offset to request the next page, or null at the end (offset pagination only)
 * @param nextPageToken opaque cursor for the next page, or null at the end (cursor pagination only)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageMeta(
        int size,
        boolean hasNext,
        @Nullable Integer offset,
        @Nullable Integer nextOffset,
        @Nullable String nextPageToken) {

    /** Builds metadata for an offset-paginated page. */
    public static PageMeta offset(int size, int offset, @Nullable Integer nextOffset) {
        return new PageMeta(size, nextOffset != null, offset, nextOffset, null);
    }

    /** Builds metadata for a cursor-paginated (keyset) page. */
    public static PageMeta cursor(int size, @Nullable String nextPageToken) {
        return new PageMeta(size, nextPageToken != null, null, null, nextPageToken);
    }
}
