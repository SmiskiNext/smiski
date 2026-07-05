package io.github.smiskinext.meetingmanagement.application.query;

import io.github.smiskinext.shared.domain.ScrollCursor;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record GetHostMeetingsQuery(
        UUID hostId, int pageSize, @Nullable ScrollCursor cursor) {

    public GetHostMeetingsQuery {
        if (pageSize < 1) pageSize = 1;
        if (pageSize > 100) pageSize = 100;
    }
}
