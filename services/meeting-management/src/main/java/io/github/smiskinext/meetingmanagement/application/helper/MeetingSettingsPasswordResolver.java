package io.github.smiskinext.meetingmanagement.application.helper;

import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.port.PasswordHasher;
import org.jspecify.annotations.Nullable;

public final class MeetingSettingsPasswordResolver {

    private MeetingSettingsPasswordResolver() {}

    public static @Nullable String normalizeRawPassword(@Nullable String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return null;
        }
        return rawPassword;
    }

    public static MeetingSettings withRawPassword(
            MeetingSettings settings, @Nullable String rawPassword, PasswordHasher passwordHasher) {
        String normalizedRawPassword = normalizeRawPassword(rawPassword);
        String hashedPassword =
                normalizedRawPassword != null ? passwordHasher.hash(normalizedRawPassword) : null;
        return new MeetingSettings(
                settings.admissionPolicy(),
                settings.allowGuest(),
                settings.maxParticipants(),
                settings.allowScreenShare(),
                settings.chatEnabled(),
                settings.allowMicrophone(),
                settings.allowVideo(),
                hashedPassword);
    }
}
