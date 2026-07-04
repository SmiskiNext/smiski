package io.github.smiskinext.meetingmanagement.application.query;

import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ParticipatedMeetingCursor;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record GetParticipatedMeetingsQuery(
        UUID userId,
        UUID requesterId,
        Set<MeetingStatus> statuses,
        int pageSize,
        @Nullable ParticipatedMeetingCursor cursor) {

    public GetParticipatedMeetingsQuery {
        statuses = Set.copyOf(statuses);
        if (pageSize < 1) pageSize = 1;
        if (pageSize > 100) pageSize = 100;
    }
}
