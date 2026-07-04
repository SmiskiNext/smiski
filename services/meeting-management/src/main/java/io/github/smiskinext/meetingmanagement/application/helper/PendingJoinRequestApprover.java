package io.github.smiskinext.meetingmanagement.application.helper;

import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.event.JoinRequestApprovedEvent;
import io.github.phunguy65.zms.meetingmanagement.domain.model.*;
import io.github.smiskinext.meetingmanagement.application.usecase.ApproveAllJoinRequestsUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.PutMeetingSettingsUseCase;
import io.github.smiskinext.meetingmanagement.domain.model.*;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitTokenRequest;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ParticipantAttributes;
import io.github.smiskinext.meetingmanagement.domain.port.JoinRequestRepository;
import io.github.smiskinext.meetingmanagement.domain.port.JoinRequestResultStore;
import io.github.smiskinext.meetingmanagement.domain.port.LiveKitPort;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Helper that bulk-approves all pending join requests for a meeting.
 *
 * <p>Shared by two callers:
 * <ol>
 *   <li>{@link ApproveAllJoinRequestsUseCase}
 *       — when host explicitly approves all.</li>
 *   <li>{@link PutMeetingSettingsUseCase}
 *       — when admissionPolicy transitions to ALLOW_ALL on a LIVE meeting.</li>
 * </ol>
 *
 * <p>This helper carries no {@code @Transactional} annotation — it participates in whatever
 * transaction the caller established. Both callers are themselves {@code @Transactional}, so this
 * method always runs inside an active transaction.
 *
 * <p>Authorization is the caller's responsibility — this helper does not check host identity.
 */
@Service
public class PendingJoinRequestApprover {

    private static final Logger log = LoggerFactory.getLogger(PendingJoinRequestApprover.class);

    private final JoinRequestRepository joinRequestRepository;
    private final ParticipationLogRepository participationLogRepository;
    private final ParticipantAvatarResolver participantAvatarResolver;
    private final LiveKitPort liveKitPort;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final JoinRequestResultStore joinRequestResultStore;

    public PendingJoinRequestApprover(
            JoinRequestRepository joinRequestRepository,
            ParticipationLogRepository participationLogRepository,
            ParticipantAvatarResolver participantAvatarResolver,
            LiveKitPort liveKitPort,
            ApplicationEventPublisher applicationEventPublisher,
            JoinRequestResultStore joinRequestResultStore) {
        this.joinRequestRepository = joinRequestRepository;
        this.participationLogRepository = participationLogRepository;
        this.participantAvatarResolver = participantAvatarResolver;
        this.liveKitPort = liveKitPort;
        this.applicationEventPublisher = applicationEventPublisher;
        this.joinRequestResultStore = joinRequestResultStore;
    }

    /**
     * Approves all pending join requests for the given meeting.
     *
     * <p>Respects the meeting's {@code maxParticipants} ceiling. Requests that cannot be approved
     * due to a full room are silently skipped (not failed).
     *
     * @param meeting    the meeting whose pending requests should be approved
     * @param approvedBy the user ID of the approver (included in the published event)
     * @return number of requests successfully approved, or a failure if any request cannot be
     *     deterministically approved
     */
    public Result<Integer, MeetingError> approveAll(Meeting meeting, UUID approvedBy) {
        List<JoinRequest> pendingRequests =
                joinRequestRepository.findPendingByMeetingId(meeting.getId().value());

        if (pendingRequests.isEmpty()) {
            return Result.success(0);
        }

        LiveKitRoomName roomName = LiveKitRoomName.fromMeetingId(meeting.getId());
        var avatarUrls = participantAvatarResolver.resolveAvatars(pendingRequests.stream()
                .flatMap(joinRequest -> joinRequest.getUserId().stream())
                .map(UserId::value)
                .toList());
        int maxParticipants = meeting.getSettings().maxParticipants();

        long remainingSlots = maxParticipants > 0
                ? maxParticipants
                        - participationLogRepository.countActiveByMeetingId(
                                meeting.getId().value())
                : Long.MAX_VALUE;

        int approvedCount = 0;
        List<UUID> failedIds = new ArrayList<>();
        List<PreparedApproval> preparedApprovals = new ArrayList<>();

        for (JoinRequest joinRequest : pendingRequests) {
            if (remainingSlots <= 0) {
                break;
            }

            var approveResult = joinRequest.approve();
            if (approveResult instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
                failedIds.add(joinRequest.getId().value());
                log.warn(
                        "Failed to approve join request '{}': {}",
                        joinRequest.getId().value(),
                        error.message());
                continue;
            }

            ParticipantRole role = joinRequest.getUserId().isPresent()
                    ? ParticipantRole.PARTICIPANT
                    : ParticipantRole.GUEST;

            LiveKitIdentity identity = joinRequest.getUserId().isPresent()
                    ? LiveKitIdentity.fromUser(
                            joinRequest.getUserId().get(), joinRequest.getDeviceId())
                    : LiveKitIdentity.forGuest(joinRequest.getDeviceId());

            var tokenResult = liveKitPort.generateToken(new LiveKitTokenRequest(
                    roomName,
                    identity,
                    joinRequest.getDisplayName(),
                    role,
                    new ParticipantAttributes(
                            joinRequest
                                    .getUserId()
                                    .map(UserId::value)
                                    .map(avatarUrls::get)
                                    .orElse(null),
                            role),
                    meeting.getSettings()));
            if (tokenResult instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
                failedIds.add(joinRequest.getId().value());
                log.warn(
                        "Token generation failed for join request '{}': {}",
                        joinRequest.getId().value(),
                        error.message());
                continue;
            }
            String token = ((Result.Success<String, MeetingError>) tokenResult).value();

            preparedApprovals.add(new PreparedApproval(joinRequest, role, identity, token));
            approvedCount++;
            remainingSlots--;
        }

        for (PreparedApproval preparedApproval : preparedApprovals) {
            JoinRequest joinRequest = preparedApproval.joinRequest();

            joinRequestRepository.updateStatus(
                    joinRequest.getId().value(), JoinRequestStatus.APPROVED);

            ParticipationLog participationLog = ParticipationLog.join(
                    meeting.getId(),
                    joinRequest.getUserId().map(UserId::value).orElse(null),
                    joinRequest.getDisplayName(),
                    preparedApproval.role(),
                    preparedApproval.identity());
            participationLogRepository.save(participationLog);
            log.debug(
                    "Recorded participation log for identity '{}' in meeting '{}'",
                    preparedApproval.identity().value(),
                    meeting.getId().value());

            applicationEventPublisher.publishEvent(new JoinRequestApprovedEvent(
                    UUID.randomUUID(),
                    meeting.getId().value(),
                    joinRequest.getId().value(),
                    approvedBy,
                    preparedApproval.token(),
                    roomName.value(),
                    Instant.now()));

            joinRequestResultStore.save(JoinRequestResult.approved(
                    joinRequest.getId().value(), preparedApproval.token(), roomName.value()));

            joinRequestRepository.removeFromQueue(
                    meeting.getId().value(), joinRequest.getId().value());
        }

        log.info(
                "Auto-approved {} pending join request(s) for meeting '{}'",
                approvedCount,
                meeting.getId().value());

        if (!failedIds.isEmpty()) {
            return Result.failure(
                    new MeetingError.PartialApprovalFailure(approvedCount, failedIds));
        }

        return Result.success(approvedCount);
    }

    private record PreparedApproval(
            JoinRequest joinRequest,
            ParticipantRole role,
            LiveKitIdentity identity,
            String token) {}
}
