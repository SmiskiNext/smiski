package io.github.smiskinext.usermanagement.domain.model.valueobject;

import org.jspecify.annotations.Nullable;

/**
 * Domain model representing verified claims from an external Google/OAuth identity provider.
 * Decoupled from any specific provider implementation (e.g. Firebase).
 */
public record GoogleAuthClaims(
        String uid,
        String email,
        @Nullable String displayName,
        @Nullable String photoUrl) {}
