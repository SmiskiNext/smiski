package io.github.smiskinext.usermanagement.application.response;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record RegisterResponse(
        UUID userId,
        String email,
        String fullName,
        @Nullable String username) {}
