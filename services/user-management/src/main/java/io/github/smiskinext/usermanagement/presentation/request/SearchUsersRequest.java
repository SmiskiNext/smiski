package io.github.smiskinext.usermanagement.presentation.request;

import io.github.phunguy65.zms.shared.domain.ScrollParams;
import io.github.smiskinext.usermanagement.application.query.SearchUsersQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.BindParam;

/**
 * Query parameters for the {@code GET /{version}/users:search} endpoint.
 *
 * <p>Implements {@link ScrollParams} to carry cursor pagination fields ({@code size},
 * {@code pageToken}) alongside the search {@code query} field. Spring MVC binds query parameters
 * to record components via {@code @ModelAttribute}; {@code @BindParam} maps the query parameter
 * name to the record component name when they differ.
 *
 * <p>Example request:
 *
 * <pre>{@code
 * GET /v1/users:search?size=20&pageToken=eyJ...&query=alice
 * }</pre>
 *
 * @param size         items per page, clamped to [1, 100] (default {@code 20})
 * @param pageTokenRaw opaque cursor from the previous response's {@code nextPageToken}; absent on
 *                     first page
 * @param queryRaw     optional case-insensitive OR search across {@code username} and {@code email}
 */
public record SearchUsersRequest(
        @Min(1) @Max(100) Integer size,
        @Nullable @BindParam("pageToken") String pageTokenRaw,
        @Nullable @BindParam("query") String queryRaw)
        implements ScrollParams {

    /** Compact constructor – applies defaults and enforces pagination invariants. */
    public SearchUsersRequest {
        if (size == null) size = 20;
        size = Math.clamp(size, 1, 100);
    }

    @Override
    public int pageSize() {
        return size;
    }

    @Override
    public Optional<String> pageToken() {
        return Optional.ofNullable(pageTokenRaw);
    }

    @Override
    public Optional<String> query() {
        return Optional.ofNullable(queryRaw);
    }

    public SearchUsersQuery toQuery() {
        return new SearchUsersQuery(queryRaw, size, pageTokenRaw);
    }
}
