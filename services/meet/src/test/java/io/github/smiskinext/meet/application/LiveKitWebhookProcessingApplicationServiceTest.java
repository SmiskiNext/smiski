package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meet.application.service.LiveKitWebhookProcessingApplicationService;
import io.github.smiskinext.meet.domain.event.MeetingCompletedEvent;
import io.github.smiskinext.meet.domain.event.MeetingStartedEvent;
import io.github.smiskinext.meet.domain.event.ParticipantJoinedEvent;
import io.github.smiskinext.meet.domain.event.ParticipantLeftEvent;
import io.github.smiskinext.meet.domain.model.AdmissionPolicy;
import io.github.smiskinext.meet.domain.model.CloseReason;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.MeetingType;
import io.github.smiskinext.meet.domain.model.ParticipantRole;
import io.github.smiskinext.meet.domain.model.ParticipationLog;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.Email;
import io.github.smiskinext.meet.domain.model.valueobject.InviteeDisplayName;
import io.github.smiskinext.meet.domain.model.valueobject.JiraIssueLink;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitParticipantSid;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitWebhookEvent;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingTimeZone;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meet.domain.model.valueobject.ParticipationLogId;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.port.ParticipationLogRepository;
import io.github.smiskinext.shared.domain.AggregateRoot;
import io.github.smiskinext.shared.domain.DomainEvent;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LiveKitWebhookProcessingApplicationServiceTest {

    private static final String TENANT_ID = "tenant-1";
    private static final UUID MEETING_UUID = UUID.randomUUID();
    private static final String ROOM_NAME = "meeting-" + MEETING_UUID;
    private static final String IDENTITY = "account-1:device-1";
    private static final String SID = "PA_session123";
    private static final Instant EVENT_TIME = Instant.parse("2025-02-01T14:00:00Z");

    private MeetingRepository meetingRepository;
    private ParticipationLogRepository participationLogRepository;
    private EventPublisher eventPublisher;
    private LiveKitWebhookProcessingApplicationService service;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        participationLogRepository = mock(ParticipationLogRepository.class);
        eventPublisher = mock(EventPublisher.class);
        service = new LiveKitWebhookProcessingApplicationService(
                meetingRepository, participationLogRepository, eventPublisher);
    }

    @Test
    void roomStartedStartsScheduledMeetingAndEnqueuesStartedEvent() {
        Meeting meeting = meeting(MeetingStatus.SCHEDULED);
        when(meetingRepository.findById(MEETING_UUID)).thenReturn(Optional.of(meeting));

        service.process(roomEvent("room_started"));

        verify(meetingRepository).save(meeting);
        assertThat(publishedEvents(meeting)).anyMatch(e -> e instanceof MeetingStartedEvent);
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.RUNNING);
    }

    @Test
    void roomStartedOnRunningMeetingIsNoOp() {
        Meeting meeting = meeting(MeetingStatus.RUNNING);
        when(meetingRepository.findById(MEETING_UUID)).thenReturn(Optional.of(meeting));

        service.process(roomEvent("room_started"));

        verify(meetingRepository, never()).save(any());
        verify(eventPublisher, never()).publishEventsOf(any());
    }

    @Test
    void roomStartedForUnknownMeetingIsNoOp() {
        when(meetingRepository.findById(MEETING_UUID)).thenReturn(Optional.empty());

        service.process(roomEvent("room_started"));

        verify(meetingRepository, never()).save(any());
        verify(eventPublisher, never()).publishEventsOf(any());
    }

    @Test
    void participantJoinedCreatesLogAssignsSidRoleAndEnqueuesJoinedEvent() {
        when(participationLogRepository.findActiveBySid(any())).thenReturn(Optional.empty());
        when(participationLogRepository.findActiveByMeetingIdAndIdentity(any(), any()))
                .thenReturn(Optional.empty());

        service.process(participantEvent("participant_joined", Map.of("role", "HOST")));

        ArgumentCaptor<ParticipationLog> captor = ArgumentCaptor.forClass(ParticipationLog.class);
        verify(participationLogRepository).save(captor.capture());
        ParticipationLog saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(ParticipantRole.HOST);
        assertThat(saved.getLivekitParticipantSid())
                .map(LiveKitParticipantSid::value)
                .contains(SID);
        assertThat(saved.getAccountId().value()).isEqualTo("account-1");
        verify(eventPublisher).publishEventsOf(saved);
        ParticipantJoinedEvent joinedEvent = publishedEvents(saved).stream()
                .filter(ParticipantJoinedEvent.class::isInstance)
                .map(ParticipantJoinedEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(joinedEvent.identity()).isEqualTo(IDENTITY);
    }

    @Test
    void participantJoinedSupersedesOrphanedActiveSessionBeforeInsert() {
        ParticipationLog orphan = activeLog();
        when(participationLogRepository.findActiveBySid(any())).thenReturn(Optional.empty());
        when(participationLogRepository.findActiveByMeetingIdAndIdentity(
                        MEETING_UUID, LiveKitIdentity.of(IDENTITY)))
                .thenReturn(Optional.of(orphan));

        service.process(participantEvent("participant_joined", Map.of("role", "PARTICIPANT")));

        ArgumentCaptor<ParticipationLog> captor = ArgumentCaptor.forClass(ParticipationLog.class);
        verify(participationLogRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<ParticipationLog> saved = captor.getAllValues();
        assertThat(saved.getFirst().getCloseReason()).contains(CloseReason.SUPERSEDED);
    }

    @Test
    void duplicateParticipantJoinedForRecordedSidCreatesNoDuplicate() {
        when(participationLogRepository.findActiveBySid(LiveKitParticipantSid.of(SID)))
                .thenReturn(Optional.of(activeLog()));

        service.process(participantEvent("participant_joined", Map.of("role", "PARTICIPANT")));

        verify(participationLogRepository, never()).save(any());
        verify(eventPublisher, never()).publishEventsOf(any());
    }

    @Test
    void participantLeftClosesMatchingActiveSessionWithLeft() {
        ParticipationLog session = activeLog();
        when(participationLogRepository.findActiveBySid(LiveKitParticipantSid.of(SID)))
                .thenReturn(Optional.of(session));

        service.process(participantEvent("participant_left", Map.of()));

        verify(participationLogRepository).save(session);
        assertThat(session.getCloseReason()).contains(CloseReason.LEFT);
        assertThat(session.getLeftAt()).contains(EVENT_TIME);
        verify(eventPublisher).publishEventsOf(session);
        ParticipantLeftEvent leftEvent = publishedEvents(session).stream()
                .filter(ParticipantLeftEvent.class::isInstance)
                .map(ParticipantLeftEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(leftEvent.identity()).isEqualTo(IDENTITY);
    }

    @Test
    void participantLeftForUnknownSidIsNoOp() {
        when(participationLogRepository.findActiveBySid(any())).thenReturn(Optional.empty());

        service.process(participantEvent("participant_left", Map.of()));

        verify(participationLogRepository, never()).save(any());
        verify(eventPublisher, never()).publishEventsOf(any());
    }

    @Test
    void roomFinishedCompletesRunningMeetingClosesLogsAndEnqueuesCompletedEvent() {
        Meeting meeting = meeting(MeetingStatus.RUNNING);
        when(meetingRepository.findById(MEETING_UUID)).thenReturn(Optional.of(meeting));
        ParticipationLog active = activeLog();
        when(participationLogRepository.findActiveByMeetingId(MEETING_UUID))
                .thenReturn(List.of(active));

        service.process(roomEvent("room_finished"));

        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.COMPLETED);
        verify(participationLogRepository).save(active);
        assertThat(active.getCloseReason()).contains(CloseReason.LEFT);
        assertThat(active.getLeftAt()).contains(EVENT_TIME);
        verify(meetingRepository).save(meeting);
        assertThat(publishedEvents(meeting)).anyMatch(e -> e instanceof MeetingCompletedEvent);
        assertThat(active.getDomainEvents()).isEmpty();
    }

    @Test
    void roomFinishedOnCompletedMeetingIsNoOp() {
        Meeting meeting = meeting(MeetingStatus.COMPLETED);
        when(meetingRepository.findById(MEETING_UUID)).thenReturn(Optional.of(meeting));

        service.process(roomEvent("room_finished"));

        verify(participationLogRepository, never()).save(any());
        verify(meetingRepository, never()).save(any());
        verify(eventPublisher, never()).publishEventsOf(any());
    }

    @Test
    void nonHandledEventsChangeNoState() {
        for (String type : List.of(
                "track_published",
                "track_unpublished",
                "egress_started",
                "ingress_started",
                "participant_connection_aborted")) {
            service.process(participantEvent(type, Map.of()));
        }

        verify(meetingRepository, never()).save(any());
        verify(participationLogRepository, never()).save(any());
        verify(eventPublisher, never()).publishEventsOf(any());
    }

    private LiveKitWebhookEvent roomEvent(String type) {
        return new LiveKitWebhookEvent(
                type,
                ROOM_NAME,
                TENANT_ID,
                null,
                null,
                Map.of(),
                UUID.randomUUID().toString(),
                EVENT_TIME);
    }

    private LiveKitWebhookEvent participantEvent(String type, Map<String, String> attributes) {
        return new LiveKitWebhookEvent(
                type,
                ROOM_NAME,
                TENANT_ID,
                IDENTITY,
                SID,
                attributes,
                UUID.randomUUID().toString(),
                EVENT_TIME);
    }

    private ParticipationLog activeLog() {
        return ParticipationLog.reconstitute(
                TenantId.of(TENANT_ID),
                ParticipationLogId.of(UUID.randomUUID()),
                MeetingId.of(MEETING_UUID),
                AccountId.of("account-1"),
                ParticipantRole.PARTICIPANT,
                LiveKitIdentity.of(IDENTITY),
                LiveKitParticipantSid.of(SID),
                EVENT_TIME.minusSeconds(60),
                null,
                null);
    }

    private Meeting meeting(MeetingStatus status) {
        MeetingSettings settings =
                new MeetingSettings(AdmissionPolicy.ALLOW_ALL, 50, true, true, true, true);
        return Meeting.reconstitute(
                TenantId.of(TENANT_ID),
                MeetingId.of(MEETING_UUID),
                AccountId.of("host-account"),
                io.github.smiskinext.meet.domain.model.valueobject.ShortCode.of("abc123def0"),
                MeetingTitle.of("Sprint"),
                "desc",
                JiraIssueLink.of("10001", "PROJ-1", "PROJ"),
                null,
                null,
                MeetingType.INSTANT,
                status,
                settings,
                MeetingTimeZone.of("UTC"),
                Email.of("host@example.com"),
                InviteeDisplayName.of("Host User"),
                UUID.randomUUID().toString(),
                0,
                EVENT_TIME.minusSeconds(600),
                EVENT_TIME.minusSeconds(600),
                null,
                null,
                null,
                null);
    }

    private static List<DomainEvent> publishedEvents(AggregateRoot<?> aggregate) {
        return aggregate.getDomainEvents();
    }
}
