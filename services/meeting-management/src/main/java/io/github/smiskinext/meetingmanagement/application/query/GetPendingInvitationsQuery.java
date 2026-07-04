package io.github.smiskinext.meetingmanagement.application.query;

import java.util.UUID;

public record GetPendingInvitationsQuery(UUID userId, UUID requesterId) {}
