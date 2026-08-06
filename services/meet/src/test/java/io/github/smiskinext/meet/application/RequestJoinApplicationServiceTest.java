package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meet.application.command.RequestJoinCommand;
import io.github.smiskinext.meet.application.result.RequestJoinResult;
import io.github.smiskinext.meet.application.service.RequestJoinApplicationService;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.event.JoinRequestApprovedEvent;
import io.github.smiskinext.meet.domain.event.JoinRequestCreatedEvent;
import io.github.smiskinext.meet.domain.model.AdmissionPolicy;
import io.github.smiskinext.meet.domain.model.InviteeRole;
import io.github.smiskinext.meet.domain.model.InviteeStatus;
import io.github.smiskinext.meet.domain.model.JoinRequest;
import io.github.smiskinext.meet.domain.model.JoinRequestResult;
import io.github.smiskinext.meet.domain.model.JoinRequestStatus;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingInvitee;
import io.github.smiskinext.meet.domain.model.MeetingType;
import io.github.smiskinext.meet.domain.model.ParticipantRole;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.Email;
import io.github.smiskinext.meet.domain.model.valueobject.InviteeDisplayName;
import io.github.smiskinext.meet.domain.model.valueobject.InviteeId;
import io.github.smiskinext.meet.domain.model.valueobject.InviterId;
import io.github.smiskinext.meet.domain.model.valueobject.JiraIssueLink;
import io.github.smiskinext.meet.domain.model.valueobject.JoinRequestId;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitTokenRequest;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingTimeZone;
import io.github.smiskinext.meet.domain.model.valueobject.ParticipantAttributes;
import io.github.smiskinext.meet.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meet.domain.port.JoinRequestRepository;
import io.github.smiskinext.meet.domain.port.JoinRequestResultStore;
import io.github.smiskinext.meet.domain.port.LiveKitPort;
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.port.ParticipationLogRepository;
import io.github.smiskinext.shared.domain.AggregateRoot;
import io.github.smiskinext.shared.domain.DomainEvent;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RequestJoinApplicationServiceTest {

    private static final String TENANT_ID = "tenant-1";
    private static final UUID MEETING_ID = UUID.randomUUID();
    private static final String HOST_ACCOUNT = "host-account";
    private static final String AVATAR_URL = "https://cdn.example.com/avatar/account-1.png";

    private MeetingRepository meetingRepository;
    private ParticipationLogRepository participationLogRepository;
    private JoinRequestRepository joinRequestRepository;
    private JoinRequestResultStore joinRequestResultStore;
    private MeetingInviteeRepository meetingInviteeRepository;
    private LiveKitPort liveKitPort;
    private EventPublisher eventPublisher;
    private RequestJoinApplicationService service;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        participationLogRepository = mock(ParticipationLogRepository.class);
        joinRequestRepository = mock(JoinRequestRepository.class);
        joinRequestResultStore = mock(JoinRequestResultStore.class);
        meetingInviteeRepository = mock(MeetingInviteeRepository.class);
        liveKitPort = mock(LiveKitPort.class);
        eventPublisher = mock(EventPublisher.class);

        service = new RequestJoinApplicationService(
                meetingRepository,
                participationLogRepository,
                joinRequestRepository,
                joinRequestResultStore,
                meetingInviteeRepository,
                liveKitPort,
                eventPublisher);
    }

    @Test
    void allowAllAdmitsCallerWithTokenCarryingAvatarAndRole() {
        stubMeeting(AdmissionPolicy.ALLOW_ALL, 50);
        when(participationLogRepository.countActiveByMeetingId(MEETING_ID)).thenReturn(3L);
        when(liveKitPort.generateToken(any())).thenReturn(Result.success("mock-token"));

        Result<RequestJoinResult, MeetingError> result = service.execute(command());

        assertThat(result.isSuccess()).isTrue();
        RequestJoinResult value =
                ((Result.Success<RequestJoinResult, MeetingError>) result).value();
        assertThat(value.status()).isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(value.token()).isEqualTo("mock-token");
        assertThat(value.roomName()).isEqualTo("meeting-" + MEETING_ID);
        assertThat(value.requestId()).isNotNull();

        ArgumentCaptor<LiveKitTokenRequest> tokenCaptor =
                ArgumentCaptor.forClass(LiveKitTokenRequest.class);
        verify(liveKitPort).generateToken(tokenCaptor.capture());
        ParticipantAttributes attributes = tokenCaptor.getValue().participantAttributes();
        assertThat(attributes.avatarUrl()).isEqualTo(AVATAR_URL);
        assertThat(attributes.role()).isEqualTo(ParticipantRole.PARTICIPANT);

        verify(participationLogRepository, never()).save(any());
        verify(joinRequestRepository, never()).save(any(), any());
    }

    @Test
    void allowAllAtCapacityReturnsMeetingFull() {
        stubMeeting(AdmissionPolicy.ALLOW_ALL, 5);
        when(participationLogRepository.countActiveByMeetingId(MEETING_ID)).thenReturn(5L);

        Result<RequestJoinResult, MeetingError> result = service.execute(command());

        assertThat(result.isFailure()).isTrue();
        MeetingError error = ((Result.Failure<RequestJoinResult, MeetingError>) result).error();
        assertThat(error).isInstanceOf(MeetingError.MeetingFull.class);
        verify(liveKitPort, never()).generateToken(any());
        verify(participationLogRepository, never()).save(any());
    }

    @Test
    void unknownMeetingReturnsNotFound() {
        when(meetingRepository.findActiveByIdWithLock(MEETING_ID)).thenReturn(Optional.empty());

        Result<RequestJoinResult, MeetingError> result = service.execute(command());

        assertThat(result.isFailure()).isTrue();
        MeetingError error = ((Result.Failure<RequestJoinResult, MeetingError>) result).error();
        assertThat(error).isInstanceOf(MeetingError.MeetingNotFound.class);
    }

    @Test
    void manualApprovalCreatesPendingRequestAndRegistersEvent() {
        stubMeeting(AdmissionPolicy.MANUAL_APPROVAL, 50);
        when(joinRequestRepository.findByDeviceId(MEETING_ID, "device-1"))
                .thenReturn(Optional.empty());

        Result<RequestJoinResult, MeetingError> result = service.execute(command());

        assertThat(result.isSuccess()).isTrue();
        RequestJoinResult value =
                ((Result.Success<RequestJoinResult, MeetingError>) result).value();
        assertThat(value.status()).isEqualTo(JoinRequestStatus.PENDING);
        assertThat(value.token()).isNull();
        assertThat(value.roomName()).isNull();

        verify(joinRequestRepository).save(any(JoinRequest.class), any());
        verify(liveKitPort, never()).generateToken(any());

        ArgumentCaptor<AggregateRoot<?>> aggregateCaptor =
                ArgumentCaptor.forClass(AggregateRoot.class);
        verify(eventPublisher).publishEventsOf(aggregateCaptor.capture());
        List<DomainEvent> events = aggregateCaptor.getValue().getDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(JoinRequestCreatedEvent.class);
        JoinRequestCreatedEvent event = (JoinRequestCreatedEvent) events.getFirst();
        assertThat(event.meetingId()).isEqualTo(MEETING_ID);
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
        assertThat(event.deviceId()).isEqualTo("device-1");
        assertThat(event.avatarUrl()).isEqualTo(AVATAR_URL);
        assertThat(event.topic()).isEqualTo("meet.join.created");
    }

    @Test
    void manualApprovalDuplicateDeviceReturnsExistingRequest() {
        stubMeeting(AdmissionPolicy.MANUAL_APPROVAL, 50);
        UUID existingId = UUID.randomUUID();
        JoinRequest existing = JoinRequest.reconstitute(
                JoinRequestId.of(existingId),
                MeetingId.of(MEETING_ID),
                AccountId.of("account-1"),
                "Alice",
                "device-1",
                AVATAR_URL,
                JoinRequestStatus.PENDING,
                Instant.now(),
                Instant.now().plusSeconds(300));
        when(joinRequestRepository.findByDeviceId(MEETING_ID, "device-1"))
                .thenReturn(Optional.of(existing));
        when(meetingInviteeRepository.findByMeetingIdAndAccountId(
                        MEETING_ID, AccountId.of("account-1")))
                .thenReturn(Optional.empty());

        Result<RequestJoinResult, MeetingError> result = service.execute(command());

        assertThat(result.isSuccess()).isTrue();
        RequestJoinResult value =
                ((Result.Success<RequestJoinResult, MeetingError>) result).value();
        assertThat(value.status()).isEqualTo(JoinRequestStatus.PENDING);
        assertThat(value.requestId()).isEqualTo(existingId);

        verify(joinRequestRepository, never()).save(any(), any());
        verify(eventPublisher, never()).publishEventsOf(any());
    }

    @Test
    void hostJoinsManualApprovalMeetingReturnsApproved() {
        stubMeeting(AdmissionPolicy.MANUAL_APPROVAL, 50);
        when(participationLogRepository.countActiveByMeetingId(MEETING_ID)).thenReturn(0L);
        when(liveKitPort.generateToken(any())).thenReturn(Result.success("host-token"));
        when(joinRequestRepository.findByDeviceId(MEETING_ID, "device-1"))
                .thenReturn(Optional.empty());

        RequestJoinCommand hostCommand = new RequestJoinCommand(
                MEETING_ID.toString(),
                TENANT_ID,
                HOST_ACCOUNT,
                "Host User",
                "device-1",
                AVATAR_URL);
        Result<RequestJoinResult, MeetingError> result = service.execute(hostCommand);

        assertThat(result.isSuccess()).isTrue();
        RequestJoinResult value =
                ((Result.Success<RequestJoinResult, MeetingError>) result).value();
        assertThat(value.status()).isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(value.token()).isEqualTo("host-token");
        assertThat(value.roomName()).isEqualTo("meeting-" + MEETING_ID);

        verify(meetingInviteeRepository, never()).findByMeetingIdAndAccountId(any(), any());
        verify(joinRequestRepository, never()).save(any(), any());
        verify(joinRequestResultStore, never()).save(any());
    }

    @Test
    void acceptedInviteeJoinsManualApprovalMeetingReturnsApproved() {
        stubMeeting(AdmissionPolicy.MANUAL_APPROVAL, 50);
        when(participationLogRepository.countActiveByMeetingId(MEETING_ID)).thenReturn(0L);
        when(liveKitPort.generateToken(any())).thenReturn(Result.success("invitee-token"));
        when(joinRequestRepository.findByDeviceId(MEETING_ID, "device-1"))
                .thenReturn(Optional.empty());

        MeetingInvitee invitee = stubInvitee("account-1", InviteeStatus.ACCEPTED);
        when(meetingInviteeRepository.findByMeetingIdAndAccountId(
                        MEETING_ID, AccountId.of("account-1")))
                .thenReturn(Optional.of(invitee));

        Result<RequestJoinResult, MeetingError> result = service.execute(command());

        assertThat(result.isSuccess()).isTrue();
        RequestJoinResult value =
                ((Result.Success<RequestJoinResult, MeetingError>) result).value();
        assertThat(value.status()).isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(value.token()).isEqualTo("invitee-token");

        verify(joinRequestRepository, never()).save(any(), any());
        verify(joinRequestResultStore, never()).save(any());
    }

    @Test
    void tentativeInviteeJoinsManualApprovalMeetingReturnsApproved() {
        stubMeeting(AdmissionPolicy.MANUAL_APPROVAL, 50);
        when(participationLogRepository.countActiveByMeetingId(MEETING_ID)).thenReturn(0L);
        when(liveKitPort.generateToken(any())).thenReturn(Result.success("invitee-token"));
        when(joinRequestRepository.findByDeviceId(MEETING_ID, "device-1"))
                .thenReturn(Optional.empty());

        MeetingInvitee invitee = stubInvitee("account-1", InviteeStatus.TENTATIVE);
        when(meetingInviteeRepository.findByMeetingIdAndAccountId(
                        MEETING_ID, AccountId.of("account-1")))
                .thenReturn(Optional.of(invitee));

        Result<RequestJoinResult, MeetingError> result = service.execute(command());

        assertThat(result.isSuccess()).isTrue();
        RequestJoinResult value =
                ((Result.Success<RequestJoinResult, MeetingError>) result).value();
        assertThat(value.status()).isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(value.token()).isEqualTo("invitee-token");

        verify(joinRequestRepository, never()).save(any(), any());
    }

    @Test
    void needsActionInviteeJoinsManualApprovalMeetingReturnsPending() {
        stubMeeting(AdmissionPolicy.MANUAL_APPROVAL, 50);
        when(joinRequestRepository.findByDeviceId(MEETING_ID, "device-1"))
                .thenReturn(Optional.empty());

        MeetingInvitee invitee = stubInvitee("account-1", InviteeStatus.NEEDS_ACTION);
        when(meetingInviteeRepository.findByMeetingIdAndAccountId(
                        MEETING_ID, AccountId.of("account-1")))
                .thenReturn(Optional.of(invitee));

        Result<RequestJoinResult, MeetingError> result = service.execute(command());

        assertThat(result.isSuccess()).isTrue();
        RequestJoinResult value =
                ((Result.Success<RequestJoinResult, MeetingError>) result).value();
        assertThat(value.status()).isEqualTo(JoinRequestStatus.PENDING);
        assertThat(value.token()).isNull();

        verify(joinRequestRepository).save(any(JoinRequest.class), any());
        verify(liveKitPort, never()).generateToken(any());
    }

    @Test
    void declinedInviteeJoinsManualApprovalMeetingReturnsPending() {
        stubMeeting(AdmissionPolicy.MANUAL_APPROVAL, 50);
        when(joinRequestRepository.findByDeviceId(MEETING_ID, "device-1"))
                .thenReturn(Optional.empty());

        MeetingInvitee invitee = stubInvitee("account-1", InviteeStatus.DECLINED);
        when(meetingInviteeRepository.findByMeetingIdAndAccountId(
                        MEETING_ID, AccountId.of("account-1")))
                .thenReturn(Optional.of(invitee));

        Result<RequestJoinResult, MeetingError> result = service.execute(command());

        assertThat(result.isSuccess()).isTrue();
        RequestJoinResult value =
                ((Result.Success<RequestJoinResult, MeetingError>) result).value();
        assertThat(value.status()).isEqualTo(JoinRequestStatus.PENDING);
        assertThat(value.token()).isNull();

        verify(joinRequestRepository).save(any(JoinRequest.class), any());
        verify(liveKitPort, never()).generateToken(any());
    }

    @Test
    void absentInviteeJoinsManualApprovalMeetingReturnsPending() {
        stubMeeting(AdmissionPolicy.MANUAL_APPROVAL, 50);
        when(joinRequestRepository.findByDeviceId(MEETING_ID, "device-1"))
                .thenReturn(Optional.empty());
        when(meetingInviteeRepository.findByMeetingIdAndAccountId(
                        MEETING_ID, AccountId.of("account-1")))
                .thenReturn(Optional.empty());

        Result<RequestJoinResult, MeetingError> result = service.execute(command());

        assertThat(result.isSuccess()).isTrue();
        RequestJoinResult value =
                ((Result.Success<RequestJoinResult, MeetingError>) result).value();
        assertThat(value.status()).isEqualTo(JoinRequestStatus.PENDING);
        assertThat(value.token()).isNull();

        verify(joinRequestRepository).save(any(JoinRequest.class), any());
        verify(liveKitPort, never()).generateToken(any());
    }

    @Test
    void eligibleCallerAtCapacityReturnsMeetingFull() {
        stubMeeting(AdmissionPolicy.MANUAL_APPROVAL, 2);
        when(participationLogRepository.countActiveByMeetingId(MEETING_ID)).thenReturn(2L);

        MeetingInvitee invitee = stubInvitee("account-1", InviteeStatus.ACCEPTED);
        when(meetingInviteeRepository.findByMeetingIdAndAccountId(
                        MEETING_ID, AccountId.of("account-1")))
                .thenReturn(Optional.of(invitee));

        Result<RequestJoinResult, MeetingError> result = service.execute(command());

        assertThat(result.isFailure()).isTrue();
        MeetingError error = ((Result.Failure<RequestJoinResult, MeetingError>) result).error();
        assertThat(error).isInstanceOf(MeetingError.MeetingFull.class);

        verify(liveKitPort, never()).generateToken(any());
        verify(joinRequestRepository, never()).save(any(), any());
    }

    @Test
    void eligibleCallerWithPendingRequestReconcilesItToApproved() {
        stubMeeting(AdmissionPolicy.MANUAL_APPROVAL, 50);
        when(participationLogRepository.countActiveByMeetingId(MEETING_ID)).thenReturn(0L);
        when(liveKitPort.generateToken(any())).thenReturn(Result.success("reconcile-token"));

        UUID existingRequestId = UUID.randomUUID();
        JoinRequest existingRequest = JoinRequest.reconstitute(
                JoinRequestId.of(existingRequestId),
                MeetingId.of(MEETING_ID),
                AccountId.of("account-1"),
                "Alice",
                "device-1",
                AVATAR_URL,
                JoinRequestStatus.PENDING,
                Instant.now(),
                Instant.now().plusSeconds(300));
        when(joinRequestRepository.findByDeviceId(MEETING_ID, "device-1"))
                .thenReturn(Optional.of(existingRequest));

        MeetingInvitee invitee = stubInvitee("account-1", InviteeStatus.ACCEPTED);
        when(meetingInviteeRepository.findByMeetingIdAndAccountId(
                        MEETING_ID, AccountId.of("account-1")))
                .thenReturn(Optional.of(invitee));

        Result<RequestJoinResult, MeetingError> result = service.execute(command());

        assertThat(result.isSuccess()).isTrue();
        RequestJoinResult value =
                ((Result.Success<RequestJoinResult, MeetingError>) result).value();
        assertThat(value.status()).isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(value.token()).isEqualTo("reconcile-token");
        assertThat(value.roomName()).isEqualTo("meeting-" + MEETING_ID);

        verify(joinRequestRepository).removeFromQueue(MEETING_ID, existingRequestId);

        ArgumentCaptor<JoinRequestResult> outcomeCaptor =
                ArgumentCaptor.forClass(JoinRequestResult.class);
        verify(joinRequestResultStore).save(outcomeCaptor.capture());
        JoinRequestResult outcome = outcomeCaptor.getValue();
        assertThat(outcome.requestId()).isEqualTo(existingRequestId);
        assertThat(outcome.status()).isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(outcome.liveKitToken()).isEqualTo(value.token());
        assertThat(outcome.roomName()).isEqualTo(value.roomName());

        ArgumentCaptor<AggregateRoot<?>> aggregateCaptor =
                ArgumentCaptor.forClass(AggregateRoot.class);
        verify(eventPublisher).publishEventsOf(aggregateCaptor.capture());
        List<DomainEvent> events = aggregateCaptor.getValue().getDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(JoinRequestApprovedEvent.class);
        JoinRequestApprovedEvent event = (JoinRequestApprovedEvent) events.getFirst();
        assertThat(event.joinRequestId()).isEqualTo(existingRequestId);
        assertThat(event.liveKitToken()).isEqualTo("reconcile-token");
        assertThat(event.roomName()).isEqualTo("meeting-" + MEETING_ID);
        assertThat(event.approvedBy()).isEqualTo("account-1");
        assertThat(existingRequest.getStatus()).isEqualTo(JoinRequestStatus.APPROVED);
    }

    @Test
    void eligibleCallerWithNoPendingRequestPublishesNoEvent() {
        stubMeeting(AdmissionPolicy.MANUAL_APPROVAL, 50);
        when(participationLogRepository.countActiveByMeetingId(MEETING_ID)).thenReturn(0L);
        when(liveKitPort.generateToken(any())).thenReturn(Result.success("token"));
        when(joinRequestRepository.findByDeviceId(MEETING_ID, "device-1"))
                .thenReturn(Optional.empty());

        MeetingInvitee invitee = stubInvitee("account-1", InviteeStatus.ACCEPTED);
        when(meetingInviteeRepository.findByMeetingIdAndAccountId(
                        MEETING_ID, AccountId.of("account-1")))
                .thenReturn(Optional.of(invitee));

        Result<RequestJoinResult, MeetingError> result = service.execute(command());

        assertThat(result.isSuccess()).isTrue();
        verify(eventPublisher, never()).publishEventsOf(any());
        verify(joinRequestResultStore, never()).save(any());
        verify(joinRequestRepository, never()).removeFromQueue(any(), any());
    }

    @Test
    void eligibleCallerTokenFailureLeavesRequestUntouched() {
        stubMeeting(AdmissionPolicy.MANUAL_APPROVAL, 50);
        when(participationLogRepository.countActiveByMeetingId(MEETING_ID)).thenReturn(0L);
        when(liveKitPort.generateToken(any()))
                .thenReturn(Result.failure(
                        new MeetingError.LiveKitUnavailable("token generation failed")));

        UUID existingRequestId = UUID.randomUUID();
        JoinRequest existingRequest = JoinRequest.reconstitute(
                JoinRequestId.of(existingRequestId),
                MeetingId.of(MEETING_ID),
                AccountId.of("account-1"),
                "Alice",
                "device-1",
                AVATAR_URL,
                JoinRequestStatus.PENDING,
                Instant.now(),
                Instant.now().plusSeconds(300));
        when(joinRequestRepository.findByDeviceId(MEETING_ID, "device-1"))
                .thenReturn(Optional.of(existingRequest));

        MeetingInvitee invitee = stubInvitee("account-1", InviteeStatus.ACCEPTED);
        when(meetingInviteeRepository.findByMeetingIdAndAccountId(
                        MEETING_ID, AccountId.of("account-1")))
                .thenReturn(Optional.of(invitee));

        Result<RequestJoinResult, MeetingError> result = service.execute(command());

        assertThat(result.isFailure()).isTrue();
        assertThat(existingRequest.getStatus()).isEqualTo(JoinRequestStatus.PENDING);
        assertThat(existingRequest.getDomainEvents()).isEmpty();
        verify(joinRequestResultStore, never()).save(any());
        verify(eventPublisher, never()).publishEventsOf(any());
        verify(joinRequestRepository, never()).removeFromQueue(any(), any());
    }

    @Test
    void eligibleCallerWithDeniedRequestIsAdmittedAndLeavesItUntouched() {
        stubMeeting(AdmissionPolicy.MANUAL_APPROVAL, 50);
        when(participationLogRepository.countActiveByMeetingId(MEETING_ID)).thenReturn(0L);
        when(liveKitPort.generateToken(any())).thenReturn(Result.success("bypass-token"));

        JoinRequest deniedRequest = JoinRequest.reconstitute(
                JoinRequestId.of(UUID.randomUUID()),
                MeetingId.of(MEETING_ID),
                AccountId.of("account-1"),
                "Alice",
                "device-1",
                AVATAR_URL,
                JoinRequestStatus.DENIED,
                Instant.now(),
                Instant.now().plusSeconds(300));
        when(joinRequestRepository.findByDeviceId(MEETING_ID, "device-1"))
                .thenReturn(Optional.of(deniedRequest));

        MeetingInvitee invitee = stubInvitee("account-1", InviteeStatus.ACCEPTED);
        when(meetingInviteeRepository.findByMeetingIdAndAccountId(
                        MEETING_ID, AccountId.of("account-1")))
                .thenReturn(Optional.of(invitee));

        Result<RequestJoinResult, MeetingError> result = service.execute(command());

        assertThat(result.isSuccess()).isTrue();
        RequestJoinResult value =
                ((Result.Success<RequestJoinResult, MeetingError>) result).value();
        assertThat(value.status()).isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(value.token()).isEqualTo("bypass-token");

        assertThat(deniedRequest.getStatus()).isEqualTo(JoinRequestStatus.DENIED);
        assertThat(deniedRequest.getDomainEvents()).isEmpty();
        verify(joinRequestResultStore, never()).save(any());
        verify(eventPublisher, never()).publishEventsOf(any());
        verify(joinRequestRepository, never()).removeFromQueue(any(), any());
    }

    private RequestJoinCommand command() {
        return new RequestJoinCommand(
                MEETING_ID.toString(), TENANT_ID, "account-1", "Alice", "device-1", AVATAR_URL);
    }

    private MeetingInvitee stubInvitee(String accountId, InviteeStatus status) {
        return MeetingInvitee.reconstitute(
                TenantId.of(TENANT_ID),
                InviteeId.of(UUID.randomUUID()),
                MeetingId.of(MEETING_ID),
                InviterId.of(HOST_ACCOUNT),
                AccountId.of(accountId),
                Email.of("alice@example.com"),
                InviteeDisplayName.of("Alice"),
                InviteeRole.REQ_PARTICIPANT,
                true,
                status,
                Instant.now(),
                status == InviteeStatus.NEEDS_ACTION ? null : Instant.now(),
                null);
    }

    private void stubMeeting(AdmissionPolicy policy, int maxParticipants) {
        stubMeeting(policy, maxParticipants, HOST_ACCOUNT);
    }

    private void stubMeeting(AdmissionPolicy policy, int maxParticipants, String hostAccount) {
        MeetingSettings settings =
                new MeetingSettings(policy, maxParticipants, true, true, true, true);
        Meeting meeting = Meeting.reconstitute(
                TenantId.of(TENANT_ID),
                MeetingId.of(MEETING_ID),
                AccountId.of(hostAccount),
                ShortCode.of("abc123def0"),
                io.github.smiskinext.meet.domain.model.valueobject.MeetingTitle.of("Sprint"),
                "desc",
                JiraIssueLink.of("10001", "PROJ-1", "PROJ"),
                null,
                null,
                MeetingType.INSTANT,
                io.github.smiskinext.meet.domain.model.MeetingStatus.RUNNING,
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
}
