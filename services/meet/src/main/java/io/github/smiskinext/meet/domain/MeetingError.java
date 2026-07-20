package io.github.smiskinext.meet.domain;

import io.github.smiskinext.meet.domain.model.InviteeStatus;
import io.github.smiskinext.meet.domain.model.JoinRequestStatus;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.shared.domain.DomainError;
import io.github.smiskinext.shared.domain.ErrorCode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain errors for the meeting-management bounded context.
 *
 * <p>Each error is a pure value exposing its {@link ErrorCode} and the positional arguments used to
 * interpolate the localized {@code detail} message (bundle key {@code error.<code>.detail}). No
 * human-readable text lives here; it is resolved from the {@code messages/meet} bundle at the HTTP
 * boundary according to the request locale.
 */
public sealed interface MeetingError extends DomainError {

    record MeetingNotFound(UUID id) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.MEETING_NOT_FOUND;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {id};
        }
    }

    record MeetingNotFoundByShortCode(String shortCode) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.MEETING_NOT_FOUND;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {shortCode};
        }
    }

    record InvalidStatusTransition(MeetingStatus from, MeetingStatus to) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.INVALID_STATUS_TRANSITION;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {from, to};
        }
    }

    record ShortCodeExhausted() implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.SHORT_CODE_EXHAUSTED;
        }
    }

    record NotAuthorized(String requesterId, String hostId) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.NOT_AUTHORIZED;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {requesterId, hostId};
        }
    }

    record NotOwner(String requesterId, String ownerId) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.NOT_OWNER;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {requesterId, ownerId};
        }
    }

    record NotParticipant(String accountId, UUID meetingId) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.NOT_PARTICIPANT;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {accountId, meetingId};
        }
    }

    record ParticipationLogNotFound(UUID meetingId, String deviceId) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.PARTICIPATION_LOG_NOT_FOUND;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {meetingId, deviceId};
        }
    }

    record LiveKitUnavailable(String detail) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.LIVEKIT_UNAVAILABLE;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {detail};
        }
    }

    record LiveKitParticipantNotFound(String roomName, String identity) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.PARTICIPANT_NOT_FOUND;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {identity, roomName};
        }
    }

    record MeetingFull(UUID meetingId, int limit) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.MEETING_FULL;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {meetingId, limit};
        }
    }

    record InviteeNotFound(String identifier) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.INVITEE_NOT_FOUND;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {identifier};
        }
    }

    record InvalidMeetingDuration(long actualMinutes, int minMinutes, int maxMinutes)
            implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.INVALID_MEETING_DURATION;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {actualMinutes, minMinutes, maxMinutes};
        }
    }

    record InvalidSettings(String detail) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.INVALID_SETTINGS;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {detail};
        }
    }

    record InvalidInviteeTransition(InviteeStatus from, InviteeStatus to) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.INVALID_INVITEE_TRANSITION;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {from, to};
        }
    }

    record JoinRequestNotFound(UUID meetingId, UUID requestId) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.JOIN_REQUEST_NOT_FOUND;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {requestId, meetingId};
        }
    }

    record JoinRequestExpired(UUID meetingId, UUID requestId) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.JOIN_REQUEST_EXPIRED;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {requestId, meetingId};
        }
    }

    record InvalidJoinRequestTransition(JoinRequestStatus from, JoinRequestStatus to)
            implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.INVALID_JOIN_REQUEST_TRANSITION;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {from, to};
        }
    }

    record NotWaitingForApproval(UUID meetingId) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.NOT_WAITING_FOR_APPROVAL;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {meetingId};
        }
    }

    /** The host attempted to mute their own tracks via the moderation endpoint. */
    record CanNotMuteSelf() implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.CANNOT_MUTE_SELF;
        }
    }

    /** The meeting is not in RUNNING status; mute operations are only valid on running meetings. */
    record MeetingNotRunning(UUID meetingId) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.MEETING_NOT_RUNNING;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {meetingId};
        }
    }

    /** The target participant has no published track of the requested source type. */
    record TrackNotFound(String identity, String source) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.TRACK_NOT_FOUND;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {source, identity};
        }
    }

    /** The host attempted to kick themselves from their own meeting. */
    record CanNotKickSelf(UUID meetingId, String hostId) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.CAN_NOT_KICK_SELF;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {hostId, meetingId};
        }
    }

    /** The kick target has no active sessions in the meeting. */
    record UserNotInMeeting(UUID meetingId, String identifier) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.USER_NOT_IN_MEETING;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {identifier, meetingId};
        }
    }

    /** The kick request provided neither or both of accountId and displayName. */
    record InvalidKickTarget(String detail) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.INVALID_KICK_TARGET;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {detail};
        }
    }

    /**
     * Some join requests failed during bulk approval while others succeeded.
     *
     * @param approvedCount number of requests that were successfully approved
     * @param failedIds     request IDs that failed approval (e.g., token generation failure)
     */
    record PartialApprovalFailure(int approvedCount, List<UUID> failedIds) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.PARTIAL_APPROVAL_FAILURE;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {approvedCount, failedIds.size()};
        }
    }

    record StartTimeInPast(Instant startTime) implements MeetingError {
        @Override
        public ErrorCode errorCode() {
            return MeetingErrorCode.MEETING_START_IN_PAST;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {startTime};
        }
    }
}
