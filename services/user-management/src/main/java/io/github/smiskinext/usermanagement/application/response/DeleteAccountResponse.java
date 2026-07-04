package io.github.smiskinext.usermanagement.application.response;

import java.time.Instant;
import java.util.UUID;

public record DeleteAccountResponse(
        UUID userId, String email, String fullName, Instant deletedAt) {}
