package io.github.smiskinext.meetingmanagement.application.query;

import io.github.phunguy65.zms.shared.domain.ScrollCursor;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record GetMeetingRecordingsQuery(
        UUID meetingId, int pageSize, @Nullable ScrollCursor cursor) {

    public GetMeetingRecordingsQuery {
        if (pageSize < 1) pageSize = 1;
        if (pageSize > 100) pageSize = 100;
    }
}
