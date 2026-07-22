package io.github.smiskinext.meet.presentation.response;

import io.github.smiskinext.meet.application.result.BatchDeleteMeetingsResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Snapshots of the meetings deleted in a batch")
public record BatchDeleteMeetingsResponse(List<DeleteMeetingResponse.Meeting> meetings) {

    public static BatchDeleteMeetingsResponse from(BatchDeleteMeetingsResult result) {
        List<DeleteMeetingResponse.Meeting> meetings =
                result.meetings().stream().map(DeleteMeetingResponse::toMeeting).toList();
        return new BatchDeleteMeetingsResponse(meetings);
    }
}
