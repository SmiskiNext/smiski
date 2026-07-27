package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import io.github.smiskinext.meet.application.command.RemoveMeetingInviteesCommand;
import io.github.smiskinext.meet.application.result.RemoveMeetingInviteesResult;
import io.github.smiskinext.meet.application.service.RemoveMeetingInviteesApplicationService;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.event.MeetingInvitationsDeletedEvent;
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

class RemoveMeetingInviteesApplicationServiceTest {

    private static final String TENANT = "tenant";
    private static final String HOST = "host";

    private MeetingRepository meetingRepository;
    private MeetingInviteeRepository inviteeRepository;
    private EventPublisher eventPublisher;
    private RemoveMeetingInviteesApplicationService service;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        inviteeRepository = mock(MeetingInviteeRepository.class);
        eventPublisher = mock(EventPublisher.class);
        when(inviteeRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new RemoveMeetingInviteesApplicationService(
                meetingRepository, inviteeRepository, eventPublisher);
    }

    @Test
    void hostRemovesInviteesReturnsRemovedSnapshots() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);
        MeetingInvitee bob = existing("bob@test.com", "bob", "Bob");
        MeetingInvitee carol = existing("carol@test.com", "carol", "Carol");
        stubActiveInvitees(List.of(bob, carol));

        Result<RemoveMeetingInviteesResult, MeetingError> result =
                service.execute(command(meeting, bob.getId().value()));

        assertThat(result.isSuccess()).isTrue();
        RemoveMeetingInviteesResult value = success(result);
        assertThat(value.invitees()).hasSize(1);
        RemoveMeetingInviteesResult.Invitee snapshot = value.invitees().getFirst();
        assertThat(snapshot.id()).isEqualTo(bob.getId().value());
        assertThat(snapshot.accountId()).isEqualTo("bob");
        assertThat(bob.getRemovedAt()).isPresent();
        assertThat(carol.getRemovedAt()).isEmpty();
    }

    @Test
    void successfulRemovePublishesExactlyOneDeletedEventCarryingOnlyRemovedInvitees() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);
        MeetingInvitee bob = existing("bob@test.com", "bob", "Bob");
        MeetingInvitee carol = existing("carol@test.com", "carol", "Carol");
        stubActiveInvitees(List.of(bob, carol));

        Result<RemoveMeetingInviteesResult, MeetingError> result =
                service.execute(command(meeting, bob.getId().value()));

        assertThat(result.isSuccess()).isTrue();
        List<DomainEvent> events = capturedEvents(meeting);
        assertThat(events).hasSize(1);
        MeetingInvitationsDeletedEvent deletedEvent =
                event(events, MeetingInvitationsDeletedEvent.class);
        assertThat(deletedEvent.invitees()).hasSize(1);
        assertThat(deletedEvent.invitees().getFirst().accountId()).isEqualTo("bob");
    }

    @Test
    void nonHostIsRejectedWithoutChangeOrEvent() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);

        Result<RemoveMeetingInviteesResult, MeetingError> result =
                service.execute(new RemoveMeetingInviteesCommand(
                        meeting.getId().value(), "intruder", TENANT, List.of(UUID.randomUUID())));

        assertThat(failure(result)).isInstanceOf(MeetingError.NotAuthorized.class);
        verify(inviteeRepository, never()).saveAll(anyList());
        verify(meetingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void unknownMeetingIsRejectedWithoutEvent() {
        when(meetingRepository.findByIdWithLock(any())).thenReturn(Optional.empty());

        Result<RemoveMeetingInviteesResult, MeetingError> result =
                service.execute(new RemoveMeetingInviteesCommand(
                        UUID.randomUUID(), HOST, TENANT, List.of(UUID.randomUUID())));

        assertThat(failure(result)).isInstanceOf(MeetingError.MeetingNotFound.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void nonScheduledStatusIsRejectedWithoutChangeOrEvent() {
        Meeting meeting = scheduledMeeting();
        MeetingInvitee bob = existing("bob@test.com", "bob", "Bob");
        meeting.start();
        meeting.clearDomainEvents();
        stubMeeting(meeting);

        Result<RemoveMeetingInviteesResult, MeetingError> result =
                service.execute(command(meeting, bob.getId().value()));

        assertThat(failure(result)).isInstanceOf(MeetingError.InvalidStatusTransition.class);
        verify(inviteeRepository, never()).saveAll(anyList());
        verify(meetingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void unknownInviteeIdFailsWholeBatchWithoutChangeOrEvent() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);
        MeetingInvitee bob = existing("bob@test.com", "bob", "Bob");
        stubActiveInvitees(List.of(bob));

        Result<RemoveMeetingInviteesResult, MeetingError> result =
                service.execute(command(meeting, bob.getId().value(), UUID.randomUUID()));

        assertThat(failure(result)).isInstanceOf(MeetingError.InviteeNotFound.class);
        assertThat(bob.getRemovedAt()).isEmpty();
        verify(inviteeRepository, never()).saveAll(anyList());
        verify(meetingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void alreadyRemovedInviteeIdFailsWholeBatchWithoutChangeOrEvent() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);
        MeetingInvitee removed = existing("bob@test.com", "bob", "Bob");
        UUID removedId = removed.getId().value();
        removed.remove();
        stubActiveInvitees(List.of());

        Result<RemoveMeetingInviteesResult, MeetingError> result =
                service.execute(command(meeting, removedId));

        assertThat(failure(result)).isInstanceOf(MeetingError.InviteeNotFound.class);
        verify(inviteeRepository, never()).saveAll(anyList());
        verify(meetingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    private void stubMeeting(Meeting meeting) {
        when(meetingRepository.findByIdWithLock(meeting.getId().value()))
                .thenReturn(Optional.of(meeting));
    }

    private void stubActiveInvitees(List<MeetingInvitee> invitees) {
        when(inviteeRepository.findByMeetingId(any())).thenReturn(new ArrayList<>(invitees));
    }

    private RemoveMeetingInviteesCommand command(Meeting meeting, UUID... inviteeIds) {
        return new RemoveMeetingInviteesCommand(
                meeting.getId().value(), HOST, TENANT, List.of(inviteeIds));
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

    private RemoveMeetingInviteesResult success(
            Result<RemoveMeetingInviteesResult, MeetingError> result) {
        return ((Result.Success<RemoveMeetingInviteesResult, MeetingError>) result).value();
    }

    private MeetingError failure(Result<RemoveMeetingInviteesResult, MeetingError> result) {
        return ((Result.Failure<RemoveMeetingInviteesResult, MeetingError>) result).error();
    }
}
