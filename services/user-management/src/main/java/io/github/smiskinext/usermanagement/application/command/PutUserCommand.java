package io.github.smiskinext.usermanagement.application.command;

import jakarta.annotation.Nullable;

/**
 * Command for replacing a user's profile via PUT. All fields are required.
 *
 * <p>All fields must be provided. A {@code null} value for {@code avatarUrl} means clear
 * the avatar.
 *
 * <p>Preferences are not included; use the dedicated preferences PUT flow for those updates.
 */
public record PutUserCommand(
        String fullName, String username, @Nullable String avatarUrl) {}
