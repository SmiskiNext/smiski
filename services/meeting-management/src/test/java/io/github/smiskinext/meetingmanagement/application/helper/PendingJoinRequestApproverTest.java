package io.github.smiskinext.meetingmanagement.application.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.phunguy65.zms.meetingmanagement.domain.model.*;
import io.github.smiskinext.meetingmanagement.domain.model.AdmissionPolicy;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequest;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.port.JoinRequestRepository;
import io.github.smiskinext.meetingmanagement.domain.port.JoinRequestResultStore;
import io.github.smiskinext.meetingmanagement.domain.port.LiveKitPort;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.MeetingId;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests for {@link PendingJoinRequestApprover} verifying partial failure handling
 * when some join requests fail during bulk approval.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PendingJoinRequestApproverTest {

    @Mock
    JoinRequestRepository joinRequestRepository;

    @Mock
    ParticipationLogRepository participationLogRepository;

    @Mock
    ParticipantAvatarResolver participantAvatarResolver;

    @Mock
    LiveKitPort liveKitPort;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    JoinRequestResultStore joinRequestResultStore;

    private PendingJoinRequestApprover approver;

    @BeforeEach
    void setUp() {
        approver = new PendingJoinRequestApprover(
                joinRequestRepository,
                participationLogRepository,
                participantAvatarResolver,
                liveKitPort,
                applicationEventPublisher,
                joinRequestResultStore);
    }

    @Test
    void approveAll_allSucceed_returnsSuccessCount() {
        UUID meetingId = UUID.randomUUID();
        UUID approvedBy = UUID.randomUUID();
        Meeting meeting = mockMeeting(meetingId, 10);

        JoinRequest r1 = createPendingRequest(meetingId);
        JoinRequest r2 = createPendingRequest(meetingId);
        when(joinRequestRepository.findPendingByMeetingId(meetingId)).thenReturn(List.of(r1, r2));
        when(participantAvatarResolver.resolveAvatars(any())).thenReturn(Map.of());
        when(participationLogRepository.countActiveByMeetingId(meetingId)).thenReturn(0L);
        when(liveKitPort.generateToken(any())).thenReturn(Result.success("token-value"));

        Result<Integer, MeetingError> result = approver.approveAll(meeting, approvedBy);

        assertThat(result.isSuccess()).isTrue();
        assertThat(((Result.Success<Integer, MeetingError>) result).value()).isEqualTo(2);
    }

    @Test
    void approveAll_oneFails_returnsPartialApprovalFailure() {
        UUID meetingId = UUID.randomUUID();
        UUID approvedBy = UUID.randomUUID();
        Meeting meeting = mockMeeting(meetingId, 10);

        JoinRequest r1 = createPendingRequest(meetingId);
        JoinRequest r2 = createPendingRequest(meetingId);
        JoinRequest r3 = createPendingRequest(meetingId);
        when(joinRequestRepository.findPendingByMeetingId(meetingId))
                .thenReturn(List.of(r1, r2, r3));
        when(participantAvatarResolver.resolveAvatars(any())).thenReturn(Map.of());
        when(participationLogRepository.countActiveByMeetingId(meetingId)).thenReturn(0L);
        when(liveKitPort.generateToken(any()))
                .thenReturn(Result.success("token-1"))
                .thenReturn(
                        Result.failure(new MeetingError.LiveKitUnavailable("connection refused")))
                .thenReturn(Result.success("token-3"));

        Result<Integer, MeetingError> result = approver.approveAll(meeting, approvedBy);

        assertThat(result.isFailure()).isTrue();
        MeetingError error = ((Result.Failure<Integer, MeetingError>) result).error();
        assertThat(error).isInstanceOf(MeetingError.PartialApprovalFailure.class);

        var partial = (MeetingError.PartialApprovalFailure) error;
        assertThat(partial.approvedCount()).isEqualTo(2);
        assertThat(partial.failedIds()).hasSize(1);
        assertThat(partial.failedIds()).contains(r2.getId().value());
    }

    @Test
    void approveAll_allFail_returnsPartialApprovalFailureWithZeroApproved() {
        UUID meetingId = UUID.randomUUID();
        UUID approvedBy = UUID.randomUUID();
        Meeting meeting = mockMeeting(meetingId, 10);

        JoinRequest r1 = createPendingRequest(meetingId);
        JoinRequest r2 = createPendingRequest(meetingId);
        when(joinRequestRepository.findPendingByMeetingId(meetingId)).thenReturn(List.of(r1, r2));
        when(participantAvatarResolver.resolveAvatars(any())).thenReturn(Map.of());
        when(participationLogRepository.countActiveByMeetingId(meetingId)).thenReturn(0L);
        when(liveKitPort.generateToken(any()))
                .thenReturn(Result.failure(new MeetingError.LiveKitUnavailable("down")));

        Result<Integer, MeetingError> result = approver.approveAll(meeting, approvedBy);

        assertThat(result.isFailure()).isTrue();
        var partial = (MeetingError.PartialApprovalFailure)
                ((Result.Failure<Integer, MeetingError>) result).error();
        assertThat(partial.approvedCount()).isEqualTo(0);
        assertThat(partial.failedIds()).hasSize(2);
    }

    @Test
    void approveAll_emptyPending_returnsZero() {
        UUID meetingId = UUID.randomUUID();
        UUID approvedBy = UUID.randomUUID();
        Meeting meeting = mockMeeting(meetingId, 10);

        when(joinRequestRepository.findPendingByMeetingId(meetingId)).thenReturn(List.of());

        Result<Integer, MeetingError> result = approver.approveAll(meeting, approvedBy);

        assertThat(result.isSuccess()).isTrue();
        assertThat(((Result.Success<Integer, MeetingError>) result).value()).isEqualTo(0);
    }

    private Meeting mockMeeting(UUID meetingId, int maxParticipants) {
        Meeting meeting = mock(Meeting.class);
        when(meeting.getId()).thenReturn(MeetingId.of(meetingId));
        MeetingSettings settings = new MeetingSettings(
                AdmissionPolicy.ALLOW_ALL, true, maxParticipants, true, true, true, true, null);
        when(meeting.getSettings()).thenReturn(settings);
        return meeting;
    }

    private JoinRequest createPendingRequest(UUID meetingId) {
        return JoinRequest.create(
                MeetingId.of(meetingId),
                UserId.of(UUID.randomUUID()),
                "User-" + UUID.randomUUID().toString().substring(0, 6),
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(300));
    }
}
