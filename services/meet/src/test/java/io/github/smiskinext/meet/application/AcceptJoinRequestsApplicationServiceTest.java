package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meet.application.command.AcceptJoinRequestsCommand;
import io.github.smiskinext.meet.application.result.AcceptJoinRequestsResult;
import io.github.smiskinext.meet.application.result.JoinDecisionItemResult;
import io.github.smiskinext.meet.application.result.JoinDecisionStatus;
import io.github.smiskinext.meet.application.service.AcceptJoinRequestsApplicationService;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.MeetingErrorCode;
import io.github.smiskinext.meet.domain.event.JoinRequestApprovedEvent;
import io.github.smiskinext.meet.domain.model.AdmissionPolicy;
import io.github.smiskinext.meet.domain.model.JoinRequest;
import io.github.smiskinext.meet.domain.model.JoinRequestResult;
import io.github.smiskinext.meet.domain.model.JoinRequestStatus;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.MeetingType;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.Email;
import io.github.smiskinext.meet.domain.model.valueobject.InviteeDisplayName;
import io.github.smiskinext.meet.domain.model.valueobject.JiraIssueLink;
import io.github.smiskinext.meet.domain.model.valueobject.JoinRequestId;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingTimeZone;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meet.domain.port.JoinRequestRepository;
import io.github.smiskinext.meet.domain.port.JoinRequestResultStore;
import io.github.smiskinext.meet.domain.port.LiveKitPort;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.port.ParticipationLogRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AcceptJoinRequestsApplicationServiceTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String HOST_ID = "host-account";
    private static final UUID MEETING_ID = UUID.randomUUID();

    private MeetingRepository meetingRepository;
    private ParticipationLogRepository participationLogRepository;
    private JoinRequestRepository joinRequestRepository;
    private JoinRequestResultStore joinRequestResultStore;
    private LiveKitPort liveKitPort;
    private EventPublisher eventPublisher;
    private AcceptJoinRequestsApplicationService service;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        participationLogRepository = mock(ParticipationLogRepository.class);
        joinRequestRepository = mock(JoinRequestRepository.class);
        joinRequestResultStore = mock(JoinRequestResultStore.class);
        liveKitPort = mock(LiveKitPort.class);
        eventPublisher = mock(EventPublisher.class);

        service = new AcceptJoinRequestsApplicationService(
                meetingRepository,
                participationLogRepository,
                joinRequestRepository,
                joinRequestResultStore,
                liveKitPort,
                eventPublisher);
    }

    @Test
    void acceptSinglePendingRequestApprovesWithTokenPersistsAndDequeues() {
        stubMeetingForTokenGeneration();
        stubMeeting(50);
        when(participationLogRepository.countActiveByMeetingId(MEETING_ID)).thenReturn(3L);
        when(liveKitPort.generateToken(any())).thenReturn(Result.success("mock-token"));
        JoinRequest request = pending("account-1", "device-1");
        when(joinRequestRepository.findById(request.getId().value()))
                .thenReturn(Optional.of(request));

        Result<AcceptJoinRequestsResult, MeetingError> result =
                service.execute(command(request.getId().value()));

        assertThat(result.isSuccess()).isTrue();
        List<JoinDecisionItemResult> items = success(result).results();
        assertThat(items).hasSize(1);
        JoinDecisionItemResult item = items.getFirst();
        assertThat(item.status()).isEqualTo(JoinDecisionStatus.APPROVED);
        assertThat(item.token()).isEqualTo("mock-token");
        assertThat(item.roomName()).isEqualTo("meeting-" + MEETING_ID);
        assertThat(item.reason()).isNull();

        verify(joinRequestResultStore).save(any(JoinRequestResult.class));
        verify(eventPublisher).publishEventsOf(request);
        verify(joinRequestRepository)
                .removeFromQueue(MEETING_ID, request.getId().value());
        assertThat(request.getStatus()).isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(request.getDomainEvents())
                .anyMatch(event -> event instanceof JoinRequestApprovedEvent);
    }

    @Test
    void acceptBatchExceedingCapacityApprovesOnlyFittingRequests() {
        stubMeetingForTokenGeneration();
        stubMeeting(3);
        when(participationLogRepository.countActiveByMeetingId(MEETING_ID)).thenReturn(1L);
        when(liveKitPort.generateToken(any())).thenReturn(Result.success("mock-token"));

        JoinRequest first = pending("account-1", "device-1");
        JoinRequest second = pending("account-2", "device-2");
        JoinRequest third = pending("account-3", "device-3");
        stubFind(first, second, third);

        Result<AcceptJoinRequestsResult, MeetingError> result = service.execute(command(
                first.getId().value(), second.getId().value(), third.getId().value()));

        List<JoinDecisionItemResult> items = success(result).results();
        assertThat(items).hasSize(3);
        long approved = items.stream()
                .filter(item -> item.status() == JoinDecisionStatus.APPROVED)
                .count();
        assertThat(approved).isEqualTo(2L);
        assertThat(items.get(2).status()).isEqualTo(JoinDecisionStatus.FAILED);
        assertThat(items.get(2).reason()).isEqualTo(MeetingErrorCode.MEETING_FULL.code());
        verify(liveKitPort, times(3)).generateToken(any());
        verify(joinRequestRepository, times(2)).removeFromQueue(eq(MEETING_ID), any());
    }

    @Test
    void acceptBatchWithUnknownTerminalAndExpiredIdsFailsOnlyThoseItems() {
        stubMeetingForTokenGeneration();
        stubMeeting(50);
        when(participationLogRepository.countActiveByMeetingId(MEETING_ID)).thenReturn(0L);
        when(liveKitPort.generateToken(any())).thenReturn(Result.success("mock-token"));

        JoinRequest pending = pending("account-1", "device-1");
        JoinRequest denied = reconstitute(
                "account-2", "device-2", JoinRequestStatus.DENIED, Instant.now().plusSeconds(300));
        JoinRequest expired = reconstitute(
                "account-3",
                "device-3",
                JoinRequestStatus.PENDING,
                Instant.now().minusSeconds(1));
        UUID unknownId = UUID.randomUUID();

        when(joinRequestRepository.findById(pending.getId().value()))
                .thenReturn(Optional.of(pending));
        when(joinRequestRepository.findById(denied.getId().value()))
                .thenReturn(Optional.of(denied));
        when(joinRequestRepository.findById(expired.getId().value()))
                .thenReturn(Optional.of(expired));
        when(joinRequestRepository.findById(unknownId)).thenReturn(Optional.empty());

        Result<AcceptJoinRequestsResult, MeetingError> result = service.execute(command(
                pending.getId().value(),
                denied.getId().value(),
                expired.getId().value(),
                unknownId));

        List<JoinDecisionItemResult> items = success(result).results();
        assertThat(items.get(0).status()).isEqualTo(JoinDecisionStatus.APPROVED);
        assertThat(items.get(1).status()).isEqualTo(JoinDecisionStatus.FAILED);
        assertThat(items.get(1).reason())
                .isEqualTo(MeetingErrorCode.INVALID_JOIN_REQUEST_TRANSITION.code());
        assertThat(items.get(2).status()).isEqualTo(JoinDecisionStatus.FAILED);
        assertThat(items.get(2).reason()).isEqualTo(MeetingErrorCode.JOIN_REQUEST_EXPIRED.code());
        assertThat(items.get(3).status()).isEqualTo(JoinDecisionStatus.FAILED);
        assertThat(items.get(3).reason()).isEqualTo(MeetingErrorCode.JOIN_REQUEST_NOT_FOUND.code());
    }

    @Test
    void allTokensAreGeneratedBeforeLockIsAcquired() {
        stubMeetingForTokenGeneration();
        stubMeeting(50);
        when(participationLogRepository.countActiveByMeetingId(MEETING_ID)).thenReturn(0L);
        when(liveKitPort.generateToken(any())).thenReturn(Result.success("mock-token"));

        JoinRequest first = pending("account-1", "device-1");
        JoinRequest second = pending("account-2", "device-2");
        stubFind(first, second);

        service.execute(command(first.getId().value(), second.getId().value()));

        InOrder inOrder = inOrder(meetingRepository, liveKitPort);
        inOrder.verify(meetingRepository).findActiveById(MEETING_ID);
        inOrder.verify(liveKitPort, times(2)).generateToken(any());
        inOrder.verify(meetingRepository).findActiveByIdWithLock(MEETING_ID);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void nonHostCallerIsRejectedBeforeAnyTokenIsGenerated() {
        stubMeetingForTokenGeneration();
        JoinRequest request = pending("account-1", "device-1");
        when(joinRequestRepository.findById(request.getId().value()))
                .thenReturn(Optional.of(request));

        Result<AcceptJoinRequestsResult, MeetingError> result =
                service.execute(new AcceptJoinRequestsCommand(
                        MEETING_ID,
                        TENANT_ID,
                        "someone-else",
                        List.of(request.getId().value())));

        assertThat(result.isFailure()).isTrue();
        assertThat(failure(result)).isInstanceOf(MeetingError.NotOwner.class);
        verify(liveKitPort, never()).generateToken(any());
        verify(meetingRepository, never()).findActiveByIdWithLock(any());
    }

    @Test
    void unknownMeetingIsRejected() {
        when(meetingRepository.findActiveById(MEETING_ID)).thenReturn(Optional.empty());

        Result<AcceptJoinRequestsResult, MeetingError> result =
                service.execute(command(UUID.randomUUID()));

        assertThat(result.isFailure()).isTrue();
        assertThat(failure(result)).isInstanceOf(MeetingError.MeetingNotFound.class);
    }

    private AcceptJoinRequestsCommand command(UUID... requestIds) {
        return new AcceptJoinRequestsCommand(MEETING_ID, TENANT_ID, HOST_ID, List.of(requestIds));
    }

    private void stubFind(JoinRequest... requests) {
        for (JoinRequest request : requests) {
            when(joinRequestRepository.findById(request.getId().value()))
                    .thenReturn(Optional.of(request));
        }
    }

    private static AcceptJoinRequestsResult success(
            Result<AcceptJoinRequestsResult, MeetingError> result) {
        return ((Result.Success<AcceptJoinRequestsResult, MeetingError>) result).value();
    }

    private static MeetingError failure(Result<AcceptJoinRequestsResult, MeetingError> result) {
        return ((Result.Failure<AcceptJoinRequestsResult, MeetingError>) result).error();
    }

    private JoinRequest pending(String accountId, String deviceId) {
        return reconstitute(
                accountId, deviceId, JoinRequestStatus.PENDING, Instant.now().plusSeconds(300));
    }

    private JoinRequest reconstitute(
            String accountId, String deviceId, JoinRequestStatus status, Instant expiresAt) {
        return JoinRequest.reconstitute(
                JoinRequestId.of(UUID.randomUUID()),
                MeetingId.of(MEETING_ID),
                AccountId.of(accountId),
                "Display " + accountId,
                deviceId,
                "https://cdn.example.com/avatar/" + accountId + ".png",
                status,
                Instant.now(),
                expiresAt);
    }

    private void stubMeeting(int maxParticipants) {
        MeetingSettings settings = new MeetingSettings(
                AdmissionPolicy.MANUAL_APPROVAL, maxParticipants, true, true, true, true);
        Meeting meeting = Meeting.reconstitute(
                TenantId.of(TENANT_ID),
                MeetingId.of(MEETING_ID),
                AccountId.of(HOST_ID),
                io.github.smiskinext.meet.domain.model.valueobject.ShortCode.of("abc123def0"),
                MeetingTitle.of("Sprint"),
                "desc",
                JiraIssueLink.of("10001", "PROJ-1", "PROJ"),
                null,
                null,
                MeetingType.INSTANT,
                MeetingStatus.RUNNING,
                settings,
                MeetingTimeZone.of("UTC"),
                Email.of("host@example.com"),
                InviteeDisplayName.of("Host User"),
                UUID.randomUUID().toString(),
                0,
                Instant.now(),
                Instant.now(),
                null,
                null,
                null,
                null);
        when(meetingRepository.findActiveByIdWithLock(eq(MEETING_ID)))
                .thenReturn(Optional.of(meeting));
    }

    private void stubMeetingForTokenGeneration() {
        MeetingSettings settings =
                new MeetingSettings(AdmissionPolicy.MANUAL_APPROVAL, 50, true, true, true, true);
        Meeting meeting = Meeting.reconstitute(
                TenantId.of(TENANT_ID),
                MeetingId.of(MEETING_ID),
                AccountId.of(HOST_ID),
                io.github.smiskinext.meet.domain.model.valueobject.ShortCode.of("abc123def0"),
                MeetingTitle.of("Sprint"),
                "desc",
                JiraIssueLink.of("10001", "PROJ-1", "PROJ"),
                null,
                null,
                MeetingType.INSTANT,
                MeetingStatus.RUNNING,
                settings,
                MeetingTimeZone.of("UTC"),
                Email.of("host@example.com"),
                InviteeDisplayName.of("Host User"),
                UUID.randomUUID().toString(),
                0,
                Instant.now(),
                Instant.now(),
                null,
                null,
                null,
                null);
        when(meetingRepository.findActiveById(eq(MEETING_ID))).thenReturn(Optional.of(meeting));
    }
}
