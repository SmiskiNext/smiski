package io.github.smiskinext.meetingmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.application.command.RequestJoinCommand;
import io.github.smiskinext.meetingmanagement.application.helper.ParticipantAvatarResolver;
import io.github.smiskinext.meetingmanagement.application.response.RequestJoinResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingStartedEvent;
import io.github.smiskinext.meetingmanagement.domain.model.AdmissionPolicy;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequest;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestStatus;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingType;
import io.github.smiskinext.meetingmanagement.domain.model.ParticipantRole;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitTokenRequest;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meetingmanagement.domain.port.JoinRequestRepository;
import io.github.smiskinext.meetingmanagement.domain.port.LiveKitPort;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import io.github.smiskinext.meetingmanagement.domain.port.PasswordHasher;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.MeetingId;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RequestJoinUseCaseTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private JoinRequestRepository joinRequestRepository;

    @Mock
    private ParticipationLogRepository participationLogRepository;

    @Mock
    private ParticipantAvatarResolver participantAvatarResolver;

    @Mock
    private LiveKitPort liveKitPort;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RequestJoinUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RequestJoinUseCase(
                meetingRepository,
                joinRequestRepository,
                participationLogRepository,
                participantAvatarResolver,
                liveKitPort,
                passwordHasher,
                eventPublisher);
        lenient()
                .when(meetingRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(liveKitPort.generateToken(any())).thenReturn(Result.success("token-value"));
        lenient()
                .when(participationLogRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(participationLogRepository.countActiveByMeetingId(any())).thenReturn(0L);
    }

    @Test
    void hostRequestingScheduledMeeting_startsMeetingPublishesEventAndReturnsApproved() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = meetingWithStatus(meetingId, hostId, MeetingStatus.SCHEDULED);
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));

        var result = useCase.execute(hostCommand(meetingId, hostId));

        assertThat(result).isInstanceOf(Result.Success.class);
        RequestJoinResponse response =
                ((Result.Success<RequestJoinResponse, MeetingError>) result).value();
        assertThat(response.status()).isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(response.token()).isEqualTo("token-value");
        assertThat(response.roomName()).isEqualTo("meeting-" + meetingId);
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.LIVE);

        verify(liveKitPort).generateToken(any());
        verify(meetingRepository).save(meeting);
        verify(participationLogRepository).save(any());
        MeetingStartedEvent event = capturedStartedEvents().getFirst();
        assertThat(event.aggregateId()).isEqualTo(meetingId);
        assertThat(event.hostId()).isEqualTo(hostId);
    }

    @Test
    void hostRequestingScheduledMeetingWithManualApproval_startsAndApprovesWithToken() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = meetingWithStatusAndAdmissionPolicy(
                meetingId, hostId, MeetingStatus.SCHEDULED, AdmissionPolicy.MANUAL_APPROVAL);
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));

        var result = useCase.execute(hostCommand(meetingId, hostId));

        assertThat(result).isInstanceOf(Result.Success.class);
        RequestJoinResponse response =
                ((Result.Success<RequestJoinResponse, MeetingError>) result).value();
        assertThat(response.status()).isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(response.token()).isEqualTo("token-value");
        assertThat(response.roomName()).isEqualTo("meeting-" + meetingId);
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.LIVE);

        verify(liveKitPort).generateToken(any());
        verify(meetingRepository).save(meeting);
        verify(participationLogRepository).save(any());
        verifyNoInteractions(joinRequestRepository);
        MeetingStartedEvent event = capturedStartedEvents().getFirst();
        assertThat(event.aggregateId()).isEqualTo(meetingId);
        assertThat(event.hostId()).isEqualTo(hostId);
    }

    @Test
    void hostRequestingLiveMeetingReturnsApprovedWithoutStartedEvent() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = meetingWithStatus(meetingId, hostId, MeetingStatus.LIVE);
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));

        var result = useCase.execute(hostCommand(meetingId, hostId));

        assertThat(result).isInstanceOf(Result.Success.class);
        RequestJoinResponse response =
                ((Result.Success<RequestJoinResponse, MeetingError>) result).value();
        assertThat(response.status()).isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.LIVE);
        assertThat(capturedEvents()).noneMatch(MeetingStartedEvent.class::isInstance);
        verify(liveKitPort).generateToken(any());
        verify(meetingRepository).save(meeting);
    }

    @Test
    void hostRequestingLiveMeetingWithManualApproval_approvesWithToken() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = meetingWithStatusAndAdmissionPolicy(
                meetingId, hostId, MeetingStatus.LIVE, AdmissionPolicy.MANUAL_APPROVAL);
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));

        var result = useCase.execute(hostCommand(meetingId, hostId));

        assertThat(result).isInstanceOf(Result.Success.class);
        RequestJoinResponse response =
                ((Result.Success<RequestJoinResponse, MeetingError>) result).value();
        assertThat(response.status()).isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(response.token()).isEqualTo("token-value");
        assertThat(response.roomName()).isEqualTo("meeting-" + meetingId);
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.LIVE);
        assertThat(capturedEvents()).noneMatch(MeetingStartedEvent.class::isInstance);

        verify(liveKitPort).generateToken(any());
        verify(meetingRepository).save(meeting);
        verify(participationLogRepository).save(any());
        verifyNoInteractions(joinRequestRepository);
    }

    @Test
    void hostRequestingEndedMeetingFailsWithInvalidStatusTransition() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = meetingWithStatus(meetingId, hostId, MeetingStatus.ENDED);
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));

        var result = useCase.execute(hostCommand(meetingId, hostId));

        assertInvalidStatus(result, MeetingStatus.ENDED);
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.ENDED);
        verify(meetingRepository, never()).save(any());
        verifyNoInteractions(liveKitPort, eventPublisher);
    }

    @Test
    void hostRequestingCancelledMeetingFailsWithInvalidStatusTransition() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = meetingWithStatus(meetingId, hostId, MeetingStatus.CANCELLED);
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));

        var result = useCase.execute(hostCommand(meetingId, hostId));

        assertInvalidStatus(result, MeetingStatus.CANCELLED);
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.CANCELLED);
        verify(meetingRepository, never()).save(any());
        verifyNoInteractions(liveKitPort, eventPublisher);
    }

    @Test
    void nonHostRequestingScheduledMeetingFailsWithInvalidStatusTransition() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = meetingWithStatus(meetingId, hostId, MeetingStatus.SCHEDULED);
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));

        var result = useCase.execute(userCommand(meetingId, UUID.randomUUID()));

        assertInvalidStatus(result, MeetingStatus.SCHEDULED);
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.SCHEDULED);
        verify(meetingRepository, never()).save(any());
        verifyNoInteractions(liveKitPort, eventPublisher);
    }

    @Test
    void nonHostRequestingLiveMeetingReturnsApproved() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Meeting meeting = meetingWithStatus(meetingId, hostId, MeetingStatus.LIVE);
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));

        var result = useCase.execute(userCommand(meetingId, userId));

        assertThat(result).isInstanceOf(Result.Success.class);
        RequestJoinResponse response =
                ((Result.Success<RequestJoinResponse, MeetingError>) result).value();
        assertThat(response.status()).isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(response.token()).isEqualTo("token-value");
        assertThat(response.roomName()).isEqualTo("meeting-" + meetingId);
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.LIVE);
        assertThat(capturedEvents()).noneMatch(MeetingStartedEvent.class::isInstance);

        ArgumentCaptor<LiveKitTokenRequest> tokenRequestCaptor =
                ArgumentCaptor.forClass(LiveKitTokenRequest.class);
        verify(liveKitPort).generateToken(tokenRequestCaptor.capture());
        assertThat(tokenRequestCaptor.getValue().role()).isEqualTo(ParticipantRole.PARTICIPANT);
        verify(meetingRepository).save(meeting);
        verify(participationLogRepository).save(any());
    }

    @Test
    void nonHostRequestingLiveMeetingWithManualApproval_returnsPending() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Meeting meeting = meetingWithStatusAndAdmissionPolicy(
                meetingId, hostId, MeetingStatus.LIVE, AdmissionPolicy.MANUAL_APPROVAL);
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        when(joinRequestRepository.findPendingByMeetingId(meetingId)).thenReturn(List.of());
        when(joinRequestRepository.findByDeviceId(meetingId, "device-1"))
                .thenReturn(Optional.empty());

        var result = useCase.execute(userCommand(meetingId, userId));

        assertThat(result).isInstanceOf(Result.Success.class);
        RequestJoinResponse response =
                ((Result.Success<RequestJoinResponse, MeetingError>) result).value();
        assertThat(response.status()).isEqualTo(JoinRequestStatus.PENDING);
        assertThat(response.token()).isNull();
        assertThat(response.roomName()).isNull();
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.LIVE);

        ArgumentCaptor<JoinRequest> joinRequestCaptor = ArgumentCaptor.forClass(JoinRequest.class);
        verify(joinRequestRepository).save(joinRequestCaptor.capture(), any());
        JoinRequest savedRequest = joinRequestCaptor.getValue();
        assertThat(savedRequest.getId().value()).isEqualTo(response.requestId());
        assertThat(savedRequest.getMeetingId().value()).isEqualTo(meetingId);
        assertThat(savedRequest.getUserId()).contains(UserId.of(userId));
        assertThat(savedRequest.getStatus()).isEqualTo(JoinRequestStatus.PENDING);
        verify(liveKitPort, never()).generateToken(any());
    }

    @Test
    void concurrentHostRequests_publishStartedEventExactlyOnceAndBothApprove() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting firstMeeting = meetingWithStatus(meetingId, hostId, MeetingStatus.SCHEDULED);
        Meeting secondMeeting = meetingWithStatus(meetingId, hostId, MeetingStatus.LIVE);
        when(meetingRepository.findByIdWithLock(meetingId))
                .thenReturn(Optional.of(firstMeeting))
                .thenReturn(Optional.of(secondMeeting));

        var firstResult = useCase.execute(hostCommand(meetingId, hostId));
        var secondResult = useCase.execute(hostCommand(meetingId, hostId));

        assertThat(firstResult).isInstanceOf(Result.Success.class);
        assertThat(secondResult).isInstanceOf(Result.Success.class);
        assertThat(((Result.Success<RequestJoinResponse, MeetingError>) firstResult)
                        .value()
                        .status())
                .isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(((Result.Success<RequestJoinResponse, MeetingError>) secondResult)
                        .value()
                        .status())
                .isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(firstMeeting.getStatus()).isEqualTo(MeetingStatus.LIVE);
        assertThat(secondMeeting.getStatus()).isEqualTo(MeetingStatus.LIVE);
        assertThat(capturedStartedEvents()).hasSize(1);
    }

    private void assertInvalidStatus(Object result, MeetingStatus from) {
        assertThat(result).isInstanceOfSatisfying(Result.Failure.class, failure -> assertThat(
                        failure.error())
                .isEqualTo(new MeetingError.InvalidStatusTransition(from, MeetingStatus.LIVE)));
    }

    private List<Object> capturedEvents() {
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        try {
            verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());
            return eventCaptor.getAllValues();
        } catch (AssertionError ignored) {
            return List.of();
        }
    }

    private List<MeetingStartedEvent> capturedStartedEvents() {
        List<MeetingStartedEvent> events = capturedEvents().stream()
                .filter(MeetingStartedEvent.class::isInstance)
                .map(MeetingStartedEvent.class::cast)
                .toList();
        assertThat(events).hasSize(1);
        return events;
    }

    private static RequestJoinCommand hostCommand(UUID meetingId, UUID hostId) {
        return userCommand(meetingId, hostId);
    }

    private static RequestJoinCommand userCommand(UUID meetingId, UUID userId) {
        return new RequestJoinCommand(meetingId, userId, "Participant", "device-1", null);
    }

    private static Meeting meetingWithStatus(UUID meetingId, UUID hostId, MeetingStatus status) {
        return meetingWithStatusAndAdmissionPolicy(
                meetingId, hostId, status, AdmissionPolicy.ALLOW_ALL);
    }

    private static Meeting meetingWithStatusAndAdmissionPolicy(
            UUID meetingId, UUID hostId, MeetingStatus status, AdmissionPolicy admissionPolicy) {
        return Meeting.reconstitute(
                MeetingId.of(meetingId),
                UserId.of(hostId),
                ShortCode.of("ABC1234567"),
                null,
                null,
                null,
                null,
                MeetingType.INSTANT,
                status,
                new MeetingSettings(admissionPolicy, true, 100, true, true, true, true, null),
                Instant.parse("2026-04-02T10:00:00Z"));
    }
}
