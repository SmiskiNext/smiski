package io.github.smiskinext.usermanagement.application.response;

import java.util.Collections;
import java.util.Map;

/**
 * Free-form preferences returned to the client. The server imposes no schema constraints;
 * whatever was stored is returned as-is.
 */
public record UserPreferencesResponse(Map<String, Object> settings) {

    public UserPreferencesResponse {
        settings =
                settings != null ? Collections.unmodifiableMap(settings) : Collections.emptyMap();
    }

    /** Returns an empty preferences response (no settings). */
    public static UserPreferencesResponse empty() {
        return new UserPreferencesResponse(Collections.emptyMap());
    }
}
