package io.github.smiskinext.usermanagement.application.helper;

import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Serializes arbitrary user preferences maps into the JSON format stored in persistence. */
@Service
public class UserPreferencesSerializer {

    private final ObjectMapper objectMapper;

    public UserPreferencesSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(Map<String, Object> settings) throws Exception {
        return settings.isEmpty() ? null : objectMapper.writeValueAsString(settings);
    }
}
