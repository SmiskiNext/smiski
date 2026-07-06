package io.github.smiskinext.meet.domain.model.valueobject;

import java.time.Instant;
import java.util.UUID;

public record ParticipatedMeetingCursor(Instant lastJoinedAt, UUID meetingId) {}
