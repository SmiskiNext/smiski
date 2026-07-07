package io.github.smiskinext.meet.domain;

import io.github.smiskinext.meet.domain.model.InviteeStatus;
import io.github.smiskinext.meet.domain.model.JoinRequestStatus;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.shared.domain.DomainError;

import java.util.List;
import java.util.UUID;

/**
 * Domain errors for the meeting-management bounded context.
 */
public sealed interface MeetingError extends DomainError {

    record MeetingNotFound(UUID id) implements MeetingError {
        @Override
        public String message() {
            return "Meeting not found: " + id;
        }
    }

    record MeetingNotFoundByShortCode(String shortCode) implements MeetingError {
        @Override
        public String message() {
            return "Meeting not found for short code: " + shortCode;
        }
    }

    record InvalidStatusTransition(MeetingStatus from, MeetingStatus to) implements MeetingError {
        @Override
        public String message() {
            return "Cannot transition meeting from " + from + " to " + to;
        }
    }

    record ShortCodeExhausted() implements MeetingError {
        @Override
        public String message() {
            return "Failed to generate a unique short code after maximum attempts";
        }
    }

    record NotAuthorized(String requesterId, String hostId) implements MeetingError {
        @Override
        public String message() {
            return "Requester " + requesterId + " is not the host of this meeting";
        }
    }

    record NotOwner(String requesterId, String ownerId) implements MeetingError {
        @Override
        public String message() {
            return "Requester " + requesterId + " does not own user scope " + ownerId;
        }
    }

    record NotParticipant(String accountId, UUID meetingId) implements MeetingError {
        @Override
        public String message() {
            return "Account " + accountId + " has not participated in meeting " + meetingId;
        }
    }

    record ParticipationLogNotFound(UUID meetingId, String deviceId) implements MeetingError {
        @Override
        public String message() {
            return "No active participation log for meeting " + meetingId + " device " + deviceId;
        }
    }

    record LiveKitUnavailable(String detail) implements MeetingError {
        @Override
        public String message() {
            return "LiveKit media server is unavailable: " + detail;
        }
    }

    record LiveKitParticipantNotFound(String roomName, String identity) implements MeetingError {
        @Override
        public String message() {
            return "LiveKit participant '" + identity + "' not found in room '" + roomName + "'";
        }
    }

    record MeetingFull(UUID meetingId, int limit) implements MeetingError {
        @Override
        public String message() {
            return "Meeting " + meetingId + " is full (limit: " + limit + ")";
        }
    }

    record InviteeNotFound(String identifier) implements MeetingError {
        @Override
        public String message() {
            return "Invitee not found: " + identifier;
        }
    }

    record InvalidMeetingDuration(long actualMinutes, int minMinutes, int maxMinutes)
            implements MeetingError {
        @Override
        public String message() {
            return "Meeting duration " + actualMinutes + " minutes is outside allowed range ["
                    + minMinutes + ", " + maxMinutes + "]";
        }
    }

    record UserServiceUnavailable(String detail) implements MeetingError {
        @Override
        public String message() {
            return "User service is unavailable: " + detail;
        }
    }

    record InvalidSettings(String detail) implements MeetingError {
        @Override
        public String message() {
            return "Invalid meeting settings: " + detail;
        }
    }

    record InvalidInviteeTransition(InviteeStatus from, InviteeStatus to) implements MeetingError {
        @Override
        public String message() {
            return "Cannot transition invitee from " + from + " to " + to;
        }
    }

    record JoinRequestNotFound(UUID meetingId, UUID requestId) implements MeetingError {
        @Override
        public String message() {
            return "Join request " + requestId + " not found for meeting: " + meetingId;
        }
    }

    record JoinRequestExpired(UUID meetingId, UUID requestId) implements MeetingError {
        @Override
        public String message() {
            return "Join request " + requestId + " has expired for meeting: " + meetingId;
        }
    }

    record InvalidJoinRequestTransition(JoinRequestStatus from, JoinRequestStatus to)
            implements MeetingError {
        @Override
        public String message() {
            return "Cannot transition join request from " + from + " to " + to;
        }
    }

    record NotWaitingForApproval(UUID meetingId) implements MeetingError {
        @Override
        public String message() {
            return "Meeting " + meetingId + " does not require manual approval";
        }
    }

    /** The host attempted to mute their own tracks via the moderation endpoint. */
    record CanNotMuteSelf() implements MeetingError {
        @Override
        public String message() {
            return "Host cannot mute themselves via moderation controls";
        }
    }

    /** The meeting is not in LIVE status; mute operations are only valid on live meetings. */
    record MeetingNotLive(UUID meetingId) implements MeetingError {
        @Override
        public String message() {
            return "Meeting " + meetingId + " is not live; mute operations require a LIVE meeting";
        }
    }

    /** The target participant has no published track of the requested source type. */
    record TrackNotFound(String identity, String source) implements MeetingError {
        @Override
        public String message() {
            return "No published '" + source + "' track found for participant '" + identity + "'";
        }
    }

    /** The host attempted to kick themselves from their own meeting. */
    record CanNotKickSelf(UUID meetingId, String hostId) implements MeetingError {
        @Override
        public String message() {
            return "Host " + hostId + " cannot kick themselves from meeting " + meetingId;
        }
    }

    /** The kick target has no active sessions in the meeting. */
    record UserNotInMeeting(UUID meetingId, String identifier) implements MeetingError {
        @Override
        public String message() {
            return "Target '" + identifier + "' has no active session in meeting " + meetingId;
        }
    }

    /** The kick request provided neither or both of accountId and displayName. */
    record InvalidKickTarget(String detail) implements MeetingError {
        @Override
        public String message() {
            return "Invalid kick target: " + detail;
        }
    }

    /**
     * The provided invite token is invalid (malformed, expired, or signature mismatch).
     *
     * @param errorCode machine-readable reason code (e.g. INVITE_TOKEN_INVALID,
     *                  INVITE_TOKEN_EXPIRED, INVITE_TOKEN_REVOKED, MEETING_NOT_FOUND,
     *                  MEETING_UNAVAILABLE)
     */
    record InvalidInviteToken(String errorCode) implements MeetingError {
        @Override
        public String message() {
            return "Invite token validation failed: " + errorCode;
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
        public String message() {
            return "Partial approval: " + approvedCount + " approved, " + failedIds.size()
                    + " failed";
        }
    }
}
