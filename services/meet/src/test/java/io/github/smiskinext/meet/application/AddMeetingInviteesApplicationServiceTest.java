package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import io.github.smiskinext.meet.application.command.AddMeetingInviteesCommand;
import io.github.smiskinext.meet.application.result.AddMeetingInviteesResult;
import io.github.smiskinext.meet.application.service.AddMeetingInviteesApplicationService;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.event.MeetingInvitationsCreatedEvent;
import io.github.smiskinext.meet.domain.model.CancelReason;
import io.github.smiskinext.meet.domain.model.InviteeRole;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingInvitee;
import io.github.smiskinext.meet.domain.model.valueobject.*;
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.DomainEvent;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AddMeetingInviteesApplicationServiceTest {

    private static final String TENANT = "tenant";
    private static final String HOST = "host";

    private MeetingRepository meetingRepository;
    private MeetingInviteeRepository inviteeRepository;
    private EventPublisher eventPublisher;
    private AddMeetingInviteesApplicationService service;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        inviteeRepository = mock(MeetingInviteeRepository.class);
        eventPublisher = mock(EventPublisher.class);
        when(inviteeRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new AddMeetingInviteesApplicationService(
                meetingRepository, inviteeRepository, eventPublisher);
    }

    @Test
    void hostAddsNewInviteesReturnsCreatedSnapshotsWithNeedsAction() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);
        stubActiveInvitees(List.of());

        Result<AddMeetingInviteesResult, MeetingError> result =
                service.execute(command(meeting, invitee("bob@test.com", "bob", "Bob")));

        assertThat(result.isSuccess()).isTrue();
        AddMeetingInviteesResult value = success(result);
        assertThat(value.invitees()).hasSize(1);
        AddMeetingInviteesResult.Invitee snapshot = value.invitees().getFirst();
        assertThat(snapshot.id()).isNotNull();
        assertThat(snapshot.accountId()).isEqualTo("bob");
        assertThat(snapshot.status()).isEqualTo("NEEDS_ACTION");
        assertThat(snapshot.role()).isEqualTo(InviteeRole.REQ_PARTICIPANT.name());
        assertThat(snapshot.respondedAt()).isNull();
    }

    @Test
    void successfulAddPublishesExactlyOneCreatedEventCarryingOnlyCreatedInvitees() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);
        stubActiveInvitees(List.of(existing("alice@test.com", "alice", "Alice")));

        Result<AddMeetingInviteesResult, MeetingError> result =
                service.execute(command(meeting, invitee("bob@test.com", "bob", "Bob")));

        assertThat(result.isSuccess()).isTrue();
        List<DomainEvent> events = capturedEvents(meeting);
        assertThat(events).hasSize(1);
        MeetingInvitationsCreatedEvent createdEvent =
                event(events, MeetingInvitationsCreatedEvent.class);
        assertThat(createdEvent.invitees()).hasSize(1);
        assertThat(createdEvent.invitees().getFirst().accountId()).isEqualTo("bob");
    }

    @Test
    void nonHostIsRejectedWithoutChangeOrEvent() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);

        Result<AddMeetingInviteesResult, MeetingError> result =
                service.execute(new AddMeetingInviteesCommand(
                        meeting.getId().value(),
                        "intruder",
                        TENANT,
                        List.of(invitee("bob@test.com", "bob", "Bob"))));

        assertThat(failure(result)).isInstanceOf(MeetingError.NotAuthorized.class);
        verify(inviteeRepository, never()).saveAll(anyList());
        verify(meetingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void unknownMeetingIsRejectedWithoutEvent() {
        when(meetingRepository.findByIdWithLock(any())).thenReturn(Optional.empty());

        Result<AddMeetingInviteesResult, MeetingError> result =
                service.execute(new AddMeetingInviteesCommand(
                        UUID.randomUUID(),
                        HOST,
                        TENANT,
                        List.of(invitee("bob@test.com", "bob", "Bob"))));

        assertThat(failure(result)).isInstanceOf(MeetingError.MeetingNotFound.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void scheduledMeetingAcceptsInviteeCreation() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);
        stubActiveInvitees(List.of());

        Result<AddMeetingInviteesResult, MeetingError> result =
                service.execute(command(meeting, invitee("bob@test.com", "bob", "Bob")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(success(result).invitees()).hasSize(1);
        verify(inviteeRepository).saveAll(anyList());
        verify(meetingRepository).save(any());
    }

    @Test
    void runningMeetingAcceptsInviteeCreation() {
        Meeting meeting = scheduledMeeting();
        meeting.start();
        meeting.clearDomainEvents();
        stubMeeting(meeting);
        stubActiveInvitees(List.of());

        Result<AddMeetingInviteesResult, MeetingError> result =
                service.execute(command(meeting, invitee("bob@test.com", "bob", "Bob")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(success(result).invitees()).hasSize(1);
        List<DomainEvent> events = capturedEvents(meeting);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(MeetingInvitationsCreatedEvent.class);
    }

    @Test
    void completedMeetingRejectsInviteeCreation() {
        Meeting meeting = scheduledMeeting();
        meeting.start();
        meeting.complete();
        meeting.clearDomainEvents();
        stubMeeting(meeting);

        Result<AddMeetingInviteesResult, MeetingError> result =
                service.execute(command(meeting, invitee("bob@test.com", "bob", "Bob")));

        assertThat(failure(result)).isInstanceOf(MeetingError.InvalidStatusTransition.class);
        verify(inviteeRepository, never()).saveAll(anyList());
        verify(meetingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void canceledMeetingRejectsInviteeCreation() {
        Meeting meeting = scheduledMeeting();
        meeting.cancel(CancelReason.HOST_CANCELED);
        meeting.clearDomainEvents();
        stubMeeting(meeting);

        Result<AddMeetingInviteesResult, MeetingError> result =
                service.execute(command(meeting, invitee("bob@test.com", "bob", "Bob")));

        assertThat(failure(result)).isInstanceOf(MeetingError.InvalidStatusTransition.class);
        verify(inviteeRepository, never()).saveAll(anyList());
        verify(meetingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void alreadyActiveAccountFailsWholeBatchWithoutChangeOrEvent() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);
        stubActiveInvitees(List.of(existing("bob@test.com", "bob", "Bob")));

        Result<AddMeetingInviteesResult, MeetingError> result = service.execute(command(
                meeting,
                invitee("dave@test.com", "dave", "Dave"),
                invitee("bob@test.com", "bob", "Bob")));

        assertThat(failure(result)).isInstanceOf(MeetingError.InviteeAlreadyExists.class);
        verify(inviteeRepository, never()).saveAll(anyList());
        verify(meetingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void reAddingSoftDeletedAccountCreatesFreshNeedsActionInvitee() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);
        stubActiveInvitees(List.of());

        Result<AddMeetingInviteesResult, MeetingError> result =
                service.execute(command(meeting, invitee("bob@test.com", "bob", "Bob")));

        assertThat(result.isSuccess()).isTrue();
        AddMeetingInviteesResult.Invitee snapshot = success(result).invitees().getFirst();
        assertThat(snapshot.status()).isEqualTo("NEEDS_ACTION");
        assertThat(snapshot.respondedAt()).isNull();
        assertThat(capturedEvents(meeting).getFirst())
                .isInstanceOf(MeetingInvitationsCreatedEvent.class);
    }

    private void stubMeeting(Meeting meeting) {
        when(meetingRepository.findByIdWithLock(meeting.getId().value()))
                .thenReturn(Optional.of(meeting));
    }

    private void stubActiveInvitees(List<MeetingInvitee> invitees) {
        when(inviteeRepository.findByMeetingId(any())).thenReturn(new ArrayList<>(invitees));
    }

    private AddMeetingInviteesCommand command(
            Meeting meeting, AddMeetingInviteesCommand.Invitee... invitees) {
        return new AddMeetingInviteesCommand(
                meeting.getId().value(), HOST, TENANT, List.of(invitees));
    }

    private AddMeetingInviteesCommand.Invitee invitee(
            String email, String accountId, String displayName) {
        return new AddMeetingInviteesCommand.Invitee(email, accountId, displayName);
    }

    private MeetingInvitee existing(String email, String accountId, String displayName) {
        return MeetingInvitee.create(
                TenantId.of(TENANT),
                MeetingId.of(UUID.randomUUID()),
                InviterId.of(HOST),
                AccountId.of(accountId),
                Email.of(email),
                InviteeDisplayName.of(displayName),
                InviteeRole.REQ_PARTICIPANT,
                true);
    }

    private Meeting scheduledMeeting() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Result<Meeting, MeetingError> result = Meeting.schedule(
                TenantId.of(TENANT),
                AccountId.of(HOST),
                MeetingTitle.of("Title"),
                "Description",
                JiraIssueLink.of("ISS-1", "PROJ-1", "PROJ"),
                MeetingTimeRange.of(start, start.plus(1, ChronoUnit.HOURS)),
                MeetingSettings.defaults(),
                MeetingTimeZone.of("UTC"),
                Email.of("host@example.com"),
                InviteeDisplayName.of("Host User"),
                ShortCode.of("ABC123DEF0"));
        Meeting meeting = ((Result.Success<Meeting, MeetingError>) result).value();
        meeting.clearDomainEvents();
        return meeting;
    }

    private List<DomainEvent> capturedEvents(Meeting meeting) {
        ArgumentCaptor<Meeting> captor = ArgumentCaptor.forClass(Meeting.class);
        verify(eventPublisher).publishEventsOf(captor.capture());
        return captor.getValue().getDomainEvents();
    }

    @SuppressWarnings("unchecked")
    private <T extends DomainEvent> T event(List<DomainEvent> events, Class<T> type) {
        return (T) events.stream().filter(type::isInstance).findFirst().orElseThrow();
    }

    private AddMeetingInviteesResult success(
            Result<AddMeetingInviteesResult, MeetingError> result) {
        return ((Result.Success<AddMeetingInviteesResult, MeetingError>) result).value();
    }

    private MeetingError failure(Result<AddMeetingInviteesResult, MeetingError> result) {
        return ((Result.Failure<AddMeetingInviteesResult, MeetingError>) result).error();
    }
}
