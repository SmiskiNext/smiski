package io.github.smiskinext.meet.application.result;

import java.util.UUID;

public record UpdateMeetingSettingsResult(
        UUID meetingId,
        String admissionPolicy,
        int maxParticipants,
        boolean allowScreenShare,
        boolean chatEnabled,
        boolean allowMicrophone,
        boolean allowVideo) {}
