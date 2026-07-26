package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meet.application.command.DeclineJoinRequestsCommand;
import io.github.smiskinext.meet.application.result.DeclineJoinRequestsResult;
import io.github.smiskinext.meet.application.result.JoinDecisionItemResult;
import io.github.smiskinext.meet.application.result.JoinDecisionStatus;
import io.github.smiskinext.meet.application.service.DeclineJoinRequestsApplicationService;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.MeetingErrorCode;
import io.github.smiskinext.meet.domain.event.JoinRequestDeniedEvent;
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
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeclineJoinRequestsApplicationServiceTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String HOST_ID = "host-account";
    private static final UUID MEETING_ID = UUID.randomUUID();

    private MeetingRepository meetingRepository;
    private JoinRequestRepository joinRequestRepository;
    private JoinRequestResultStore joinRequestResultStore;
    private EventPublisher eventPublisher;
    private DeclineJoinRequestsApplicationService service;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        joinRequestRepository = mock(JoinRequestRepository.class);
        joinRequestResultStore = mock(JoinRequestResultStore.class);
        eventPublisher = mock(EventPublisher.class);

        service = new DeclineJoinRequestsApplicationService(
                meetingRepository, joinRequestRepository, joinRequestResultStore, eventPublisher);
    }

    @Test
    void declinePendingRequestDeniesPersistsAndDequeues() {
        stubMeeting();
        JoinRequest request = pending("account-1", "device-1");
        when(joinRequestRepository.findById(request.getId().value()))
                .thenReturn(Optional.of(request));

        Result<DeclineJoinRequestsResult, MeetingError> result =
                service.execute(command(request.getId().value()));

        List<JoinDecisionItemResult> items = success(result).results();
        assertThat(items).hasSize(1);
        JoinDecisionItemResult item = items.getFirst();
        assertThat(item.status()).isEqualTo(JoinDecisionStatus.DENIED);
        assertThat(item.token()).isNull();
        assertThat(item.roomName()).isNull();

        verify(joinRequestResultStore).save(any(JoinRequestResult.class));
        verify(eventPublisher).publishEventsOf(request);
        verify(joinRequestRepository)
                .removeFromQueue(MEETING_ID, request.getId().value());
        assertThat(request.getStatus()).isEqualTo(JoinRequestStatus.DENIED);
        assertThat(request.getDomainEvents())
                .anyMatch(event -> event instanceof JoinRequestDeniedEvent);
    }

    @Test
    void declineBestEffortFailsUnknownIdOnly() {
        stubMeeting();
        JoinRequest request = pending("account-1", "device-1");
        UUID unknownId = UUID.randomUUID();
        when(joinRequestRepository.findById(request.getId().value()))
                .thenReturn(Optional.of(request));
        when(joinRequestRepository.findById(unknownId)).thenReturn(Optional.empty());

        Result<DeclineJoinRequestsResult, MeetingError> result =
                service.execute(command(request.getId().value(), unknownId));

        List<JoinDecisionItemResult> items = success(result).results();
        assertThat(items.get(0).status()).isEqualTo(JoinDecisionStatus.DENIED);
        assertThat(items.get(1).status()).isEqualTo(JoinDecisionStatus.FAILED);
        assertThat(items.get(1).reason()).isEqualTo(MeetingErrorCode.JOIN_REQUEST_NOT_FOUND.code());
        verify(joinRequestRepository)
                .removeFromQueue(MEETING_ID, request.getId().value());
        verify(joinRequestRepository, never()).removeFromQueue(eq(MEETING_ID), eq(unknownId));
    }

    @Test
    void nonHostCallerIsRejected() {
        stubMeeting();

        Result<DeclineJoinRequestsResult, MeetingError> result =
                service.execute(new DeclineJoinRequestsCommand(
                        MEETING_ID, TENANT_ID, "someone-else", List.of(UUID.randomUUID())));

        assertThat(result.isFailure()).isTrue();
        assertThat(failure(result)).isInstanceOf(MeetingError.NotOwner.class);
        verify(eventPublisher, never()).publishEventsOf(any());
    }

    @Test
    void unknownMeetingIsRejected() {
        when(meetingRepository.findActiveByIdWithLock(MEETING_ID)).thenReturn(Optional.empty());

        Result<DeclineJoinRequestsResult, MeetingError> result =
                service.execute(command(UUID.randomUUID()));

        assertThat(result.isFailure()).isTrue();
        assertThat(failure(result)).isInstanceOf(MeetingError.MeetingNotFound.class);
    }

    private DeclineJoinRequestsCommand command(UUID... requestIds) {
        return new DeclineJoinRequestsCommand(MEETING_ID, TENANT_ID, HOST_ID, List.of(requestIds));
    }

    private static DeclineJoinRequestsResult success(
            Result<DeclineJoinRequestsResult, MeetingError> result) {
        return ((Result.Success<DeclineJoinRequestsResult, MeetingError>) result).value();
    }

    private static MeetingError failure(Result<DeclineJoinRequestsResult, MeetingError> result) {
        return ((Result.Failure<DeclineJoinRequestsResult, MeetingError>) result).error();
    }

    private JoinRequest pending(String accountId, String deviceId) {
        return JoinRequest.reconstitute(
                JoinRequestId.of(UUID.randomUUID()),
                MeetingId.of(MEETING_ID),
                AccountId.of(accountId),
                "Display " + accountId,
                deviceId,
                null,
                JoinRequestStatus.PENDING,
                Instant.now(),
                Instant.now().plusSeconds(300));
    }

    private void stubMeeting() {
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
        when(meetingRepository.findActiveByIdWithLock(eq(MEETING_ID)))
                .thenReturn(Optional.of(meeting));
    }
}
