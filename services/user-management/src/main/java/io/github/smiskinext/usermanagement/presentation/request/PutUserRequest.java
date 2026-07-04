package io.github.smiskinext.usermanagement.presentation.request;

import io.github.smiskinext.usermanagement.application.command.PutUserCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PUT request DTO for replacing a user's profile. All fields are required in the request body.
 *
 * <p>All fields must be present in the request body. Use {@code null} for {@code avatarUrl}
 * to clear the avatar.
 *
 * <p>Preferences are not included; use {@code PUT /api/v1/me/preferences} for preference updates.
 */
public record PutUserRequest(
        @NotNull @NotBlank @Size(max = 255) String fullName,

        @NotNull @NotBlank @Size(min = 3, max = 30) @Pattern(
                regexp = "^[a-zA-Z0-9_-]+$",
                message = "Username must contain only letters, digits, _ or -")
        String username,

        @Size(max = 2048) String avatarUrl) {

    public PutUserCommand toCommand() {
        return new PutUserCommand(fullName, username, avatarUrl);
    }
}
