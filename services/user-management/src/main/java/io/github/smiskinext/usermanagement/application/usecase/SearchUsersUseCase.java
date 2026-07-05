package io.github.smiskinext.usermanagement.application.usecase;

import io.github.smiskinext.shared.domain.CursorPageResponse;
import io.github.smiskinext.shared.domain.ScrollCursor;
import io.github.smiskinext.usermanagement.application.helper.UserPreferencesParser;
import io.github.smiskinext.usermanagement.application.query.SearchUsersQuery;
import io.github.smiskinext.usermanagement.application.response.UserResponse;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import io.github.smiskinext.usermanagement.domain.port.UserScrollFilter;
import io.github.smiskinext.usermanagement.domain.projection.UserSummary;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchUsersUseCase {

    private final UserRepository userRepository;
    private final UserPreferencesParser preferencesParser;

    public SearchUsersUseCase(
            UserRepository userRepository, UserPreferencesParser preferencesParser) {
        this.userRepository = userRepository;
        this.preferencesParser = preferencesParser;
    }

    /**
     * Searches users by optional query string with keyset pagination.
     *
     * <p>Cursor decoding is delegated to the caller (controller) via {@link CursorTokenEncoder}.
     * The returned {@link CursorPageResponse} carries the raw domain items and a flag indicating
     * whether more pages exist; the controller is responsible for encoding the next page token and
     * wrapping the result in the HTTP response envelope.
     *
     * @param query  pagination and filter parameters
     * @param cursor decoded cursor from the previous page, or {@code null} for the first page
     * @return a page of {@link UserResponse} items
     */
    @Transactional(readOnly = true)
    public CursorPageResponse<UserResponse> execute(SearchUsersQuery query, ScrollCursor cursor) {
        UserScrollFilter filter = new UserScrollFilter(query.query().orElse(null));

        CursorPageResponse<UserSummary> pageResult =
                userRepository.searchSummaries(cursor, query.pageSize(), filter);

        var items = pageResult.items().stream().map(this::toResponse).toList();

        return new CursorPageResponse<>(items, pageResult.pageSize(), pageResult.hasNext());
    }

    private UserResponse toResponse(UserSummary summary) {
        return new UserResponse(
                summary.id(),
                summary.email(),
                summary.fullName(),
                summary.username(),
                summary.avatarUrl(),
                summary.authProvider(),
                preferencesParser.parseAsResponse(Optional.ofNullable(summary.preferences())),
                summary.createdAt(),
                summary.updatedAt());
    }
}
