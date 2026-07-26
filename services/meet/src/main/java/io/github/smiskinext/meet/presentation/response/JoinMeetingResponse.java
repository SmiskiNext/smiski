package io.github.smiskinext.meet.presentation.response;

import io.github.smiskinext.meet.application.result.RequestJoinResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(description = "Response for a join attempt")
public record JoinMeetingResponse(
        @Schema(description = "Identifier of the join request or participation session")
        UUID requestId,

        @Schema(description = "Outcome of the join", example = "APPROVED")
        String status,

        @Schema(description = "LiveKit access token; present only when APPROVED", nullable = true)
        @Nullable String token,

        @Schema(description = "LiveKit room name; present only when APPROVED", nullable = true)
        @Nullable String roomName) {

    public static JoinMeetingResponse from(RequestJoinResult result) {
        return new JoinMeetingResponse(
                result.requestId(), result.status().name(), result.token(), result.roomName());
    }
}
