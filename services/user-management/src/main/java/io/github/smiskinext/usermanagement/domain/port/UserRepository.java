package io.github.smiskinext.usermanagement.domain.port;

import io.github.phunguy65.zms.shared.domain.CursorPageResponse;
import io.github.phunguy65.zms.shared.domain.ScrollCursor;
import io.github.phunguy65.zms.shared.domain.valueobject.Email;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.domain.model.User;
import io.github.smiskinext.usermanagement.domain.model.valueobject.Username;
import io.github.smiskinext.usermanagement.domain.projection.UserSummary;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Outbound port: persistence operations for the {@link User} aggregate. */
public interface UserRepository {

    Optional<User> findByEmail(Email email);

    Optional<User> findById(UserId id);

    /** Returns the user only if {@code deleted_at IS NULL}. */
    Optional<User> findActiveById(UserId id);

    /** Returns the user only if {@code deleted_at IS NULL}. */
    Optional<User> findActiveByEmail(Email email);

    /** Returns the active user with the given Firebase Google UID, or empty if not found. */
    Optional<User> findActiveByGoogleUid(String googleUid);

    User save(User user);

    boolean existsByEmail(Email email);

    /** Returns {@code true} only if an active (non-deleted) user with this email exists. */
    boolean existsActiveByEmail(Email email);

    /** Returns {@code true} only if an active (non-deleted) user with this username exists. */
    boolean existsActiveByUsername(Username username);

    /** Returns the active user with the given username, or empty if not found. */
    Optional<User> findActiveByUsername(Username username);

    /**
     * Batch-fetch active (non-deleted) users by email.
     * Invalid or malformed emails are silently skipped.
     * Missing emails are absent from the result list.
     */
    List<User> findActiveByEmails(Collection<String> emails);

    /**
     * Returns a keyset-scrolled page of active (non-deleted) users matching the given filter.
     * Results are ordered by {@code (created_at DESC, id DESC)}.
     *
     * @param cursor decoded cursor from the previous page, or {@code null} for the first page
     * @param size   page size (max 100)
     * @param filter optional search filter; use {@link UserScrollFilter#empty()} for no filtering
     */
    CursorPageResponse<User> searchUsers(
            @Nullable ScrollCursor cursor, int size, UserScrollFilter filter);

    // ── Read-only projection methods (GET use cases) ───────────────────────

    /**
     * Returns a lightweight {@link UserSummary} projection for an active user.
     * Does not reconstitute the full aggregate — no value-object wrapping, no sensitive fields.
     */
    Optional<UserSummary> findSummaryActiveById(UserId id);

    /**
     * Batch-fetch {@link UserSummary} projections for active (non-deleted) users by email.
     * Invalid or malformed emails are silently skipped.
     * Missing emails are absent from the result list.
     */
    List<UserSummary> findSummariesByEmails(Collection<String> emails);

    /**
     * Batch-fetch {@link UserSummary} projections for active (non-deleted) users by ID.
     * Missing IDs are absent from the result list.
     */
    List<UserSummary> findSummariesByIds(Collection<UserId> userIds);

    /**
     * Returns a keyset-scrolled page of {@link UserSummary} projections for active users.
     * Results are ordered by {@code (created_at DESC, id DESC)}.
     *
     * @param cursor decoded cursor from the previous page, or {@code null} for the first page
     * @param size   page size (max 100)
     * @param filter optional search filter
     */
    CursorPageResponse<UserSummary> searchSummaries(
            @Nullable ScrollCursor cursor, int size, UserScrollFilter filter);
}
