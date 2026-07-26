package io.github.smiskinext.meet.application.mapper;

import io.github.smiskinext.meet.application.result.JoinDecisionItemResult;
import io.github.smiskinext.meet.domain.MeetingErrorCode;

import java.util.UUID;

/**
 * Maps a host decision outcome to its per-item {@link JoinDecisionItemResult}.
 *
 * <p>Centralizes the per-item shape shared by accept and decline: {@code APPROVED} items carry the
 * LiveKit token and room name, {@code DENIED} items carry neither, and {@code FAILED} items carry a
 * machine-readable reason derived from the item's {@link MeetingErrorCode} and never a token.
 */
public final class JoinDecisionMapper {

    private JoinDecisionMapper() {}

    public static JoinDecisionItemResult approved(UUID requestId, String token, String roomName) {
        return JoinDecisionItemResult.approved(requestId, token, roomName);
    }

    public static JoinDecisionItemResult denied(UUID requestId) {
        return JoinDecisionItemResult.denied(requestId);
    }

    public static JoinDecisionItemResult failed(UUID requestId, MeetingErrorCode reason) {
        return JoinDecisionItemResult.failed(requestId, reason.code());
    }
}
