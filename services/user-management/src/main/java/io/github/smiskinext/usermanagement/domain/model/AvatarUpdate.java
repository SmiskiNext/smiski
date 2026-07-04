package io.github.smiskinext.usermanagement.domain.model;

/**
 * Discriminated union encoding the caller's explicit intent for the {@code avatarUrl}
 * field in a full profile replacement operation.
 *
 * <ul>
 *   <li>{@link Set} — replace with the new URL.</li>
 *   <li>{@link Clear} — remove the avatar.</li>
 * </ul>
 */
public sealed interface AvatarUpdate {

    /** Replace the avatar URL with a new value. */
    record Set(String url) implements AvatarUpdate {}

    /** Remove the avatar URL (set to {@code null}). */
    record Clear() implements AvatarUpdate {}
}
