package io.github.smiskinext.usermanagement.application.helper;

import io.github.smiskinext.usermanagement.application.response.UserPreferencesResponse;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses a user's raw JSON preferences string into a {@link UserPreferencesResponse}.
 * No schema is enforced; any valid JSON object is accepted. Returns an empty map on
 * absent or unparseable input.
 */
@Service
public class UserPreferencesParser {

    private static final Logger log = LoggerFactory.getLogger(UserPreferencesParser.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public UserPreferencesParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Parses the raw JSON string into a {@link UserPreferencesResponse}. Returns empty on failure. */
    public UserPreferencesResponse parseAsResponse(Optional<String> preferencesJson) {
        return preferencesJson
                .filter(s -> !s.isBlank())
                .map(s -> {
                    try {
                        Map<String, Object> map = objectMapper.readValue(s, MAP_TYPE);
                        return new UserPreferencesResponse(map);
                    } catch (Exception e) {
                        log.warn(
                                "Failed to parse user preferences JSON, returning empty: {}",
                                e.getMessage());
                        return UserPreferencesResponse.empty();
                    }
                })
                .orElseGet(UserPreferencesResponse::empty);
    }
}
