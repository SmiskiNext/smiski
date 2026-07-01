package io.github.phunguy65.zms.shared.infrastructure.web;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Generic JSON response envelope for cursor-based (keyset) scroll list endpoints.
 *
 * <p>All keyset-scroll list endpoints in any module MUST wrap their item list in this record and
 * then pass it to {@link JsendResponse#success(Object)}.
 *
 * <p>Example JSON shape:
 *
 * <pre>{@code
 * {
 *   "status": "success",
 *   "data": {
 *     "content": [...],
 *     "size": 20,
 *     "nextPageToken": "eyJjcmVhdGVkQXQiOiIxNzA..."
 *   }
 * }
 * }</pre>
 *
 * <p>When {@code nextPageToken} is {@code null}, the client has reached the end of results.
 *
 * @param <T> the item type for each element in {@code content}
 */
public record CursorScrollResponse<T>(
        List<T> content, int size, @Nullable String nextPageToken) {}
