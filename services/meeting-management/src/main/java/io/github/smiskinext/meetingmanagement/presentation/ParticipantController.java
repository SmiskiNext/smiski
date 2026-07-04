package io.github.smiskinext.meetingmanagement.presentation;

import io.github.smiskinext.meetingmanagement.application.command.KickParticipantCommand;
import io.github.smiskinext.meetingmanagement.application.command.MuteAllParticipantsCommand;
import io.github.smiskinext.meetingmanagement.application.command.MuteParticipantTrackCommand;
import io.github.smiskinext.meetingmanagement.application.query.GetParticipantsQuery;
import io.github.smiskinext.meetingmanagement.application.response.ParticipantListItemResponse;
import io.github.smiskinext.meetingmanagement.application.usecase.GetParticipantsUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.KickParticipantUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.MuteAllParticipantsUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.MuteParticipantTrackUseCase;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.infrastructure.web.JsendResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Participants", description = "Meeting participant management")
public class ParticipantController extends BaseController {

    private final GetParticipantsUseCase getParticipantsUseCase;
    private final KickParticipantUseCase kickParticipantUseCase;
    private final MuteAllParticipantsUseCase muteAllParticipantsUseCase;
    private final MuteParticipantTrackUseCase muteParticipantTrackUseCase;

    public ParticipantController(
            GetParticipantsUseCase getParticipantsUseCase,
            KickParticipantUseCase kickParticipantUseCase,
            MuteAllParticipantsUseCase muteAllParticipantsUseCase,
            MuteParticipantTrackUseCase muteParticipantTrackUseCase) {
        this.getParticipantsUseCase = getParticipantsUseCase;
        this.kickParticipantUseCase = kickParticipantUseCase;
        this.muteAllParticipantsUseCase = muteAllParticipantsUseCase;
        this.muteParticipantTrackUseCase = muteParticipantTrackUseCase;
    }

    @Operation(summary = "List participants of a meeting")
    @GetMapping(value = "/{version}/meetings/{id}/participants", version = "1.0")
    public ResponseEntity<JsendResponse<List<ParticipantListItemResponse>>> getParticipants(
            @PathVariable UUID id) {
        return switch (getParticipantsUseCase.execute(new GetParticipantsQuery(id))) {
            case Result.Success<List<ParticipantListItemResponse>, MeetingError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<List<ParticipantListItemResponse>, MeetingError> f ->
                errorResponse(f.error());
        };
    }

    /**
     * Kicks an active participant from a live meeting. Only the meeting host can perform this action.
     *
     * <p>Exactly one of {@code userId} (registered participant) or {@code displayName} (guest) must
     * be provided.
     */
    @Operation(summary = "Kick a participant from a meeting")
    @PostMapping(value = "/{version}/meetings/{id}/participants:kick", version = "1.0")
    public ResponseEntity<JsendResponse<Void>> kickParticipant(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String displayName,
            Authentication authentication) {
        UUID requesterId = extractUserId(authentication);
        if (requesterId == null) {
            return unauthenticated();
        }

        var command = new KickParticipantCommand(id, requesterId, userId, displayName);
        return switch (kickParticipantUseCase.execute(command)) {
            case Result.Success<Void, MeetingError> _ ->
                ResponseEntity.noContent().build();
            case Result.Failure<Void, MeetingError> f -> errorResponse(f.error());
        };
    }

    /**
     * Mutes the microphone of all active participants in a live meeting.
     * Only the meeting host can perform this action. HOST and GUEST sessions are excluded.
     */
    @Operation(summary = "Mute all participant microphones")
    @PostMapping(value = "/{version}/meetings/{id}/participants:muteAll", version = "1.0")
    public ResponseEntity<JsendResponse<Void>> muteAllParticipants(
            @PathVariable @Parameter(description = "Meeting UUID") UUID id,
            Authentication authentication) {
        UUID requesterId = extractUserId(authentication);
        if (requesterId == null) {
            return unauthenticated();
        }

        var command = new MuteAllParticipantsCommand(id, requesterId);
        return switch (muteAllParticipantsUseCase.execute(command)) {
            case Result.Success<Void, MeetingError> _ ->
                ResponseEntity.noContent().build();
            case Result.Failure<Void, MeetingError> f -> errorResponse(f.error());
        };
    }

    /**
     * Mutes a specific participant's microphone or camera track.
     * The host cannot mute their own tracks via this endpoint.
     */
    @Operation(summary = "Mute a participant's track")
    @PostMapping(
            value = "/{version}/meetings/{id}/participants/{identity}:muteTrack",
            version = "1.0")
    public ResponseEntity<JsendResponse<Void>> muteParticipantTrack(
            @PathVariable @Parameter(description = "Meeting UUID") UUID id,
            @PathVariable @Parameter(description = "LiveKit participant identity") String identity,
            @RequestParam @Parameter(description = "Track source: microphone or camera")
                    String source,
            Authentication authentication) {
        UUID requesterId = extractUserId(authentication);
        if (requesterId == null) {
            return unauthenticated();
        }

        var command = new MuteParticipantTrackCommand(id, requesterId, identity, source);
        return switch (muteParticipantTrackUseCase.execute(command)) {
            case Result.Success<Void, MeetingError> _ ->
                ResponseEntity.noContent().build();
            case Result.Failure<Void, MeetingError> f -> errorResponse(f.error());
        };
    }
}
