package io.github.smiskinext.meet.domain;

import io.github.smiskinext.shared.domain.ErrorCategory;
import io.github.smiskinext.shared.domain.ErrorCode;

/**
 * Machine-readable error codes for the meeting-management bounded context, surfaced as the
 * {@code code} member of {@code application/problem+json} responses.
 */
public enum MeetingErrorCode implements ErrorCode {
    MEETING_NOT_FOUND(ErrorCategory.NOT_FOUND),
    INVALID_STATUS_TRANSITION(ErrorCategory.CONFLICT),
    SHORT_CODE_EXHAUSTED(ErrorCategory.INTERNAL),
    NOT_AUTHORIZED(ErrorCategory.FORBIDDEN),
    NOT_OWNER(ErrorCategory.FORBIDDEN),
    NOT_PARTICIPANT(ErrorCategory.FORBIDDEN),
    PARTICIPATION_LOG_NOT_FOUND(ErrorCategory.NOT_FOUND),
    LIVEKIT_UNAVAILABLE(ErrorCategory.UNAVAILABLE),
    MEETING_FULL(ErrorCategory.CONFLICT),
    INVITEE_NOT_FOUND(ErrorCategory.NOT_FOUND),
    INVITEE_ALREADY_EXISTS(ErrorCategory.CONFLICT),
    INVALID_MEETING_DURATION(ErrorCategory.VALIDATION),
    USER_SERVICE_UNAVAILABLE(ErrorCategory.UNAVAILABLE),
    INVALID_SETTINGS(ErrorCategory.VALIDATION),
    INVALID_INVITEE_TRANSITION(ErrorCategory.CONFLICT),
    JOIN_REQUEST_NOT_FOUND(ErrorCategory.NOT_FOUND),
    JOIN_REQUEST_EXPIRED(ErrorCategory.CONFLICT),
    INVALID_JOIN_REQUEST_TRANSITION(ErrorCategory.CONFLICT),
    NOT_WAITING_FOR_APPROVAL(ErrorCategory.CONFLICT),
    CAN_NOT_KICK_SELF(ErrorCategory.VALIDATION),
    USER_NOT_IN_MEETING(ErrorCategory.NOT_FOUND),
    INVALID_KICK_TARGET(ErrorCategory.VALIDATION),
    PARTIAL_APPROVAL_FAILURE(ErrorCategory.CONFLICT),
    CANNOT_MUTE_SELF(ErrorCategory.VALIDATION),
    MEETING_NOT_RUNNING(ErrorCategory.CONFLICT),
    TRACK_NOT_FOUND(ErrorCategory.NOT_FOUND),
    PARTICIPANT_NOT_FOUND(ErrorCategory.NOT_FOUND),
    MEETING_START_IN_PAST(ErrorCategory.VALIDATION),
    CANNOT_DELETE_RUNNING_MEETING(ErrorCategory.CONFLICT);

    private final ErrorCategory category;

    MeetingErrorCode(ErrorCategory category) {
        this.category = category;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public ErrorCategory category() {
        return category;
    }
}
