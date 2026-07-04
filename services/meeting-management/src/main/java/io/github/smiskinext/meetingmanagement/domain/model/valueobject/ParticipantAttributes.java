package io.github.smiskinext.meetingmanagement.domain.model.valueobject;

import io.github.smiskinext.meetingmanagement.domain.model.ParticipantRole;
import io.github.phunguy65.zms.shared.domain.ValueObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record ParticipantAttributes(@Nullable String avatarUrl, ParticipantRole role)
        implements ValueObject {

    public ParticipantAttributes {
        Objects.requireNonNull(role, "role must not be null");
    }

    public Map<String, String> toMap() {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("role", role.name());
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            attributes.put("avatarUrl", avatarUrl);
        }
        return Map.copyOf(attributes);
    }
}
