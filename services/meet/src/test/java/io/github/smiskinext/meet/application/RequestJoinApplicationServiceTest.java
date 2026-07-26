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
import io.github.smiskinext.meet.domain.event.JoinRequestCreatedEvent;
import io.github.smiskinext.meet.domain.model.AdmissionPolicy;
import io.github.smiskinext.meet.domain.model.JoinRequest;
import io.github.smiskinext.meet.domain.model.JoinRequestStatus;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingType;
import io.github.smiskinext.meet.domain.model.ParticipantRole;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.Email;
import io.github.smiskinext.meet.domain.model.valueobject.InviteeDisplayName;
import io.github.smiskinext.meet.domain.model.valueobject.JiraIssueLink;
import io.github.smiskinext.meet.domain.model.valueobject.JoinRequestId;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitTokenRequest;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingTimeZone;
import io.github.smiskinext.meet.domain.model.valueobject.ParticipantAttributes;
import io.github.smiskinext.meet.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meet.domain.port.JoinRequestRepository;
import io.github.smiskinext.meet.domain.port.LiveKitPort;
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
    private static final String AVATAR_URL = "https://cdn.example.com/avatar/account-1.png";

    private MeetingRepository meetingRepository;
    private ParticipationLogRepository participationLogRepository;
    private JoinRequestRepository joinRequestRepository;
    private LiveKitPort liveKitPort;
    private EventPublisher eventPublisher;
    private RequestJoinApplicationService service;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        participationLogRepository = mock(ParticipationLogRepository.class);
        joinRequestRepository = mock(JoinRequestRepository.class);
        liveKitPort = mock(LiveKitPort.class);
        eventPublisher = mock(EventPublisher.class);

        service = new RequestJoinApplicationService(
                meetingRepository,
                participationLogRepository,
                joinRequestRepository,
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

        Result<RequestJoinResult, MeetingError> result = service.execute(command());

        assertThat(result.isSuccess()).isTrue();
        RequestJoinResult value =
                ((Result.Success<RequestJoinResult, MeetingError>) result).value();
        assertThat(value.status()).isEqualTo(JoinRequestStatus.PENDING);
        assertThat(value.requestId()).isEqualTo(existingId);

        verify(joinRequestRepository, never()).save(any(), any());
        verify(eventPublisher, never()).publishEventsOf(any());
    }

    private RequestJoinCommand command() {
        return new RequestJoinCommand(
                MEETING_ID.toString(), TENANT_ID, "account-1", "Alice", "device-1", AVATAR_URL);
    }

    private void stubMeeting(AdmissionPolicy policy, int maxParticipants) {
        MeetingSettings settings =
                new MeetingSettings(policy, maxParticipants, true, true, true, true);
        Meeting meeting = Meeting.reconstitute(
                TenantId.of(TENANT_ID),
                MeetingId.of(MEETING_ID),
                AccountId.of("host-account"),
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
