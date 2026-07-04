package io.github.smiskinext.meetingmanagement.application.query;

import java.util.UUID;

public record GetJoinRequestsQuery(UUID meetingId, UUID requesterId, int pageSize, int offset) {

    public GetJoinRequestsQuery {
        if (pageSize < 1) pageSize = 1;
        if (pageSize > 100) pageSize = 100;
        if (offset < 0) offset = 0;
    }
}
