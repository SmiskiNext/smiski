package io.github.smiskinext.meetingmanagement.presentation;

import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.MeetingErrorCode;
import io.github.phunguy65.zms.shared.infrastructure.web.FailData;
import io.github.phunguy65.zms.shared.infrastructure.web.JsendResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

/**
 * Shared controller base that centralises {@link MeetingError} → HTTP status and error code
 * mapping for all meeting-management controllers.
 */
abstract class BaseController {

    @SuppressWarnings("unchecked")
    protected <T> ResponseEntity<JsendResponse<T>> errorResponse(MeetingError error) {
        if (error instanceof MeetingError.PartialApprovalFailure paf) {
            return (ResponseEntity<JsendResponse<T>>)
                    (ResponseEntity<?>) ResponseEntity.status(HttpStatus.MULTI_STATUS)
                            .body(JsendResponse.fail(new PartialApprovalFailData(
                                    paf.message(),
                                    MeetingErrorCode.PARTIAL_APPROVAL_FAILURE,
                                    List.of(),
                                    paf.approvedCount(),
                                    paf.failedIds())));
        }

        HttpStatus status =
                switch (error) {
                    case MeetingError.MeetingNotFound e -> HttpStatus.NOT_FOUND;
                    case MeetingError.MeetingNotFoundByShortCode e -> HttpStatus.NOT_FOUND;
                    case MeetingError.RecordingNotFound e -> HttpStatus.NOT_FOUND;
                    case MeetingError.ParticipationLogNotFound e -> HttpStatus.NOT_FOUND;
                    case MeetingError.JoinRequestNotFound e -> HttpStatus.NOT_FOUND;
                    case MeetingError.InvalidStatusTransition e -> HttpStatus.CONFLICT;
                    case MeetingError.InvalidRecordingTransition e -> HttpStatus.CONFLICT;
                    case MeetingError.RecordingAlreadyActive e -> HttpStatus.CONFLICT;
                    case MeetingError.NoActiveRecording e -> HttpStatus.CONFLICT;
                    case MeetingError.MeetingFull e -> HttpStatus.CONFLICT;
                    case MeetingError.InvalidJoinRequestTransition e -> HttpStatus.CONFLICT;
                    case MeetingError.NotAuthorized e -> HttpStatus.FORBIDDEN;
                    case MeetingError.NotOwner e -> HttpStatus.FORBIDDEN;
                    case MeetingError.NotParticipant e -> HttpStatus.FORBIDDEN;
                    case MeetingError.GuestNotAllowed e -> HttpStatus.FORBIDDEN;
                    case MeetingError.ShortCodeExhausted e -> HttpStatus.SERVICE_UNAVAILABLE;
                    case MeetingError.LiveKitUnavailable e -> HttpStatus.SERVICE_UNAVAILABLE;
                    case MeetingError.LiveKitParticipantNotFound e -> HttpStatus.NOT_FOUND;
                    case MeetingError.MeetingNotLive e -> HttpStatus.UNPROCESSABLE_ENTITY;
                    case MeetingError.UserServiceUnavailable e -> HttpStatus.SERVICE_UNAVAILABLE;
                    case MeetingError.InviteeNotFound e -> HttpStatus.NOT_FOUND;
                    case MeetingError.InvalidMeetingDuration e -> HttpStatus.BAD_REQUEST;
                    case MeetingError.InvalidSettings e -> HttpStatus.BAD_REQUEST;
                    case MeetingError.InvalidInviteeTransition e -> HttpStatus.CONFLICT;
                    case MeetingError.InvalidPassword e -> HttpStatus.FORBIDDEN;
                    case MeetingError.JoinRequestExpired e -> HttpStatus.GONE;
                    case MeetingError.NotWaitingForApproval e -> HttpStatus.UNPROCESSABLE_CONTENT;
                    case MeetingError.CanNotKickSelf e -> HttpStatus.BAD_REQUEST;
                    case MeetingError.UserNotInMeeting e -> HttpStatus.NOT_FOUND;
                    case MeetingError.InvalidKickTarget e -> HttpStatus.BAD_REQUEST;
                    case MeetingError.InvalidInviteToken e -> HttpStatus.UNPROCESSABLE_ENTITY;
                    case MeetingError.PartialApprovalFailure e -> HttpStatus.MULTI_STATUS;
                    case MeetingError.CanNotMuteSelf e -> HttpStatus.UNPROCESSABLE_ENTITY;
                    case MeetingError.TrackNotFound e -> HttpStatus.UNPROCESSABLE_ENTITY;
                };
        MeetingErrorCode code =
                switch (error) {
                    case MeetingError.MeetingNotFound e -> MeetingErrorCode.MEETING_NOT_FOUND;
                    case MeetingError.MeetingNotFoundByShortCode e ->
                        MeetingErrorCode.MEETING_NOT_FOUND;
                    case MeetingError.RecordingNotFound e -> MeetingErrorCode.RECORDING_NOT_FOUND;
                    case MeetingError.ParticipationLogNotFound e ->
                        MeetingErrorCode.PARTICIPATION_LOG_NOT_FOUND;
                    case MeetingError.InvalidStatusTransition e ->
                        MeetingErrorCode.INVALID_STATUS_TRANSITION;
                    case MeetingError.InvalidRecordingTransition e ->
                        MeetingErrorCode.INVALID_RECORDING_TRANSITION;
                    case MeetingError.RecordingAlreadyActive e ->
                        MeetingErrorCode.RECORDING_ALREADY_ACTIVE;
                    case MeetingError.NoActiveRecording e -> MeetingErrorCode.NO_ACTIVE_RECORDING;
                    case MeetingError.MeetingFull e -> MeetingErrorCode.MEETING_FULL;
                    case MeetingError.ShortCodeExhausted e -> MeetingErrorCode.SHORT_CODE_EXHAUSTED;
                    case MeetingError.LiveKitUnavailable e -> MeetingErrorCode.LIVEKIT_UNAVAILABLE;
                    case MeetingError.LiveKitParticipantNotFound e ->
                        MeetingErrorCode.PARTICIPANT_NOT_FOUND;
                    case MeetingError.MeetingNotLive e ->
                        MeetingErrorCode.INVALID_STATUS_TRANSITION;
                    case MeetingError.UserServiceUnavailable e ->
                        MeetingErrorCode.USER_SERVICE_UNAVAILABLE;
                    case MeetingError.InviteeNotFound e -> MeetingErrorCode.INVITEE_NOT_FOUND;
                    case MeetingError.InvalidMeetingDuration e ->
                        MeetingErrorCode.INVALID_MEETING_DURATION;
                    case MeetingError.InvalidSettings e -> MeetingErrorCode.INVALID_SETTINGS;
                    case MeetingError.InvalidInviteeTransition e ->
                        MeetingErrorCode.INVALID_INVITEE_TRANSITION;
                    case MeetingError.InvalidPassword e -> MeetingErrorCode.INVALID_PASSWORD;
                    case MeetingError.GuestNotAllowed e -> MeetingErrorCode.GUEST_NOT_ALLOWED;
                    case MeetingError.NotAuthorized e -> MeetingErrorCode.NOT_AUTHORIZED;
                    case MeetingError.NotOwner e -> MeetingErrorCode.NOT_OWNER;
                    case MeetingError.NotParticipant e -> MeetingErrorCode.NOT_PARTICIPANT;
                    case MeetingError.JoinRequestNotFound e ->
                        MeetingErrorCode.JOIN_REQUEST_NOT_FOUND;
                    case MeetingError.JoinRequestExpired e -> MeetingErrorCode.JOIN_REQUEST_EXPIRED;
                    case MeetingError.InvalidJoinRequestTransition e ->
                        MeetingErrorCode.INVALID_JOIN_REQUEST_TRANSITION;
                    case MeetingError.NotWaitingForApproval e ->
                        MeetingErrorCode.NOT_WAITING_FOR_APPROVAL;
                    case MeetingError.CanNotKickSelf e -> MeetingErrorCode.CAN_NOT_KICK_SELF;
                    case MeetingError.UserNotInMeeting e -> MeetingErrorCode.USER_NOT_IN_MEETING;
                    case MeetingError.InvalidKickTarget e -> MeetingErrorCode.INVALID_KICK_TARGET;
                    case MeetingError.InvalidInviteToken e -> MeetingErrorCode.INVALID_INVITE_TOKEN;
                    case MeetingError.PartialApprovalFailure e ->
                        MeetingErrorCode.PARTIAL_APPROVAL_FAILURE;
                    case MeetingError.CanNotMuteSelf e -> MeetingErrorCode.CANNOT_MUTE_SELF;
                    case MeetingError.TrackNotFound e -> MeetingErrorCode.TRACK_NOT_FOUND;
                };
        return (ResponseEntity<JsendResponse<T>>) (ResponseEntity<?>) ResponseEntity.status(status)
                .body(JsendResponse.fail(new FailData(error.message(), code, List.of())));
    }

    protected UUID extractUserId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof String principalId)) {
            return null;
        }
        try {
            return UUID.fromString(principalId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    protected <T> ResponseEntity<JsendResponse<T>> unauthenticated() {
        return (ResponseEntity<JsendResponse<T>>)
                (ResponseEntity<?>) ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(JsendResponse.error("Authentication required"));
    }
}
