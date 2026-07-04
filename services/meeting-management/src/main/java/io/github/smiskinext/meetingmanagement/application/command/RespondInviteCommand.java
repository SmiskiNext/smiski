package io.github.smiskinext.meetingmanagement.application.command;

import java.util.UUID;

public record RespondInviteCommand(
        UUID meetingId, UUID userId, UUID requesterId, InviteeResponseType response) {}
