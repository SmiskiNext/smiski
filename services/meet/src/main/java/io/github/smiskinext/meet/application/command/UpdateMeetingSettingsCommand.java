package io.github.smiskinext.meet.application.command;

import io.github.smiskinext.shared.application.Command;

import java.util.UUID;

public record UpdateMeetingSettingsCommand(
        UUID meetingId,
        String tenantId,
        String accountId,
        String admissionPolicy,
        int maxParticipants,
        boolean allowScreenShare,
        boolean chatEnabled,
        boolean allowMicrophone,
        boolean allowVideo)
        implements Command {}
