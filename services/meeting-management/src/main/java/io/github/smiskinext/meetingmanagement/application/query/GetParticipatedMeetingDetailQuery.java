package io.github.smiskinext.meetingmanagement.application.query;

import java.util.UUID;

public record GetParticipatedMeetingDetailQuery(UUID userId, UUID meetingId, UUID requesterId) {}
