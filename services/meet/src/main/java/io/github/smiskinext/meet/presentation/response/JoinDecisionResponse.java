package io.github.smiskinext.meet.presentation.response;

import io.github.smiskinext.meet.application.result.JoinDecisionItemResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Response for a host accept/decline decision, carrying one entry per submitted request id.
 */
@Schema(description = "Per-item outcome of a host accept/decline decision")
public record JoinDecisionResponse(
        @Schema(description = "One result per submitted request id, in submission order")
        List<DecisionItem> results) {

    public static JoinDecisionResponse from(
            io.github.smiskinext.meet.application.result.AcceptJoinRequestsResult result) {
        return new JoinDecisionResponse(
                result.results().stream().map(DecisionItem::from).toList());
    }

    public static JoinDecisionResponse from(
            io.github.smiskinext.meet.application.result.DeclineJoinRequestsResult result) {
        return new JoinDecisionResponse(
                result.results().stream().map(DecisionItem::from).toList());
    }

    @Schema(description = "Outcome of a single submitted join request")
    public record DecisionItem(
            @Schema(description = "Identifier of the submitted join request")
            String requestId,

            @Schema(description = "Outcome of the request", example = "APPROVED")
            String status,

            @Schema(
                    description = "LiveKit access token; present only when APPROVED",
                    nullable = true)
            @Nullable String token,

            @Schema(description = "LiveKit room name; present only when APPROVED", nullable = true)
            @Nullable String roomName,

            @Schema(
                    description = "Machine-readable reason; present only when FAILED",
                    nullable = true)
            @Nullable String reason) {

        public static DecisionItem from(JoinDecisionItemResult item) {
            return new DecisionItem(
                    item.requestId().toString(),
                    item.status().name(),
                    item.token(),
                    item.roomName(),
                    item.reason());
        }
    }
}
