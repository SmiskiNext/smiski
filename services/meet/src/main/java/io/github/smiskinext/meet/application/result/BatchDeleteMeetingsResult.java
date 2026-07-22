package io.github.smiskinext.meet.application.result;

import java.util.List;

/**
 * Result of an atomic batch soft-delete: a snapshot of every meeting that was deleted.
 *
 * @param meetings the deleted meeting snapshots in request order
 */
public record BatchDeleteMeetingsResult(List<DeleteMeetingResult> meetings) {

    public BatchDeleteMeetingsResult {
        meetings = List.copyOf(meetings);
    }
}
