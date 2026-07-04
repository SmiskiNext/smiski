package io.github.smiskinext.usermanagement.application.response;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserPreferencesResponse preferences) {}
