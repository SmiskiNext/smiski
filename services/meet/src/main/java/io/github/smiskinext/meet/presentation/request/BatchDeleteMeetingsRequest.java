package io.github.smiskinext.meet.presentation.request;

import io.github.smiskinext.meet.application.command.BatchDeleteMeetingsCommand;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

@Schema(description = "Batch meeting soft-delete request")
public record BatchDeleteMeetingsRequest(
        @NotEmpty @Schema(description = "Identifiers of the meetings to delete")
        List<UUID> meetingIds) {

    public BatchDeleteMeetingsCommand toCommand(String accountId, String tenantId) {
        return new BatchDeleteMeetingsCommand(meetingIds, tenantId, accountId);
    }
}
