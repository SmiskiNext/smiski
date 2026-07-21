package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import io.github.smiskinext.meet.application.command.UpdateMeetingInviteesCommand;
import io.github.smiskinext.meet.application.result.UpdateMeetingInviteesResult;
import io.github.smiskinext.meet.application.service.UpdateMeetingInviteesApplicationService;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.event.MeetingInvitationsCreatedEvent;
import io.github.smiskinext.meet.domain.event.MeetingInvitationsDeletedEvent;
import io.github.smiskinext.meet.domain.event.MeetingInvitationsUpdatedEvent;
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

class UpdateMeetingInviteesApplicationServiceTest {

    private static final String TENANT = "tenant";
    private static final String HOST = "host";

    private MeetingRepository meetingRepository;
    private MeetingInviteeRepository inviteeRepository;
    private EventPublisher eventPublisher;
    private UpdateMeetingInviteesApplicationService service;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        inviteeRepository = mock(MeetingInviteeRepository.class);
        eventPublisher = mock(EventPublisher.class);
        when(inviteeRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new UpdateMeetingInviteesApplicationService(
                meetingRepository, inviteeRepository, eventPublisher);
    }

    @Test
    void newInviteeIsCreatedAsNeedsAction() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);
        stubActiveInvitees(List.of());

        Result<UpdateMeetingInviteesResult, MeetingError> result =
                service.execute(command(meeting, invitee("bob@test.com", "bob", "Bob")));

        assertThat(result.isSuccess()).isTrue();
        UpdateMeetingInviteesResult value = success(result);
        assertThat(value.invitees()).hasSize(1);
        assertThat(value.invitees().getFirst().status()).isEqualTo("NEEDS_ACTION");
        assertThat(value.invitees().getFirst().accountId()).isEqualTo("bob");

        List<DomainEvent> events = capturedEvents(meeting);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(MeetingInvitationsCreatedEvent.class);
    }

    @Test
    void existingInviteeDisplayNameUpdatedPreservesOtherFields() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);
        MeetingInvitee existing = existing("bob@test.com", "bob", "Bob");
        stubActiveInvitees(List.of(existing));

        Result<UpdateMeetingInviteesResult, MeetingError> result =
                service.execute(command(meeting, invitee("bob@test.com", "bob", "Bobby")));

        assertThat(result.isSuccess()).isTrue();
        UpdateMeetingInviteesResult.Invitee snapshot =
                success(result).invitees().getFirst();
        assertThat(snapshot.displayName()).isEqualTo("Bobby");
        assertThat(snapshot.email()).isEqualTo("bob@test.com");
        assertThat(snapshot.role()).isEqualTo(InviteeRole.REQ_PARTICIPANT.name());
        assertThat(snapshot.status()).isEqualTo("NEEDS_ACTION");

        List<DomainEvent> events = capturedEvents(meeting);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(MeetingInvitationsUpdatedEvent.class);
    }

    @Test
    void absentInviteeIsSoftDeleted() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);
        MeetingInvitee existing = existing("bob@test.com", "bob", "Bob");
        stubActiveInvitees(List.of(existing));

        Result<UpdateMeetingInviteesResult, MeetingError> result =
                service.execute(command(meeting));

        assertThat(result.isSuccess()).isTrue();
        assertThat(success(result).invitees()).isEmpty();
        assertThat(existing.getRemovedAt()).isPresent();

        List<DomainEvent> events = capturedEvents(meeting);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(MeetingInvitationsDeletedEvent.class);
    }

    @Test
    void unchangedInviteeIsLeftIntactAndNoOpPublishesNoEvent() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);
        MeetingInvitee existing = existing("bob@test.com", "bob", "Bob");
        stubActiveInvitees(List.of(existing));

        Result<UpdateMeetingInviteesResult, MeetingError> result =
                service.execute(command(meeting, invitee("bob@test.com", "bob", "Bob")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(success(result).invitees()).hasSize(1);
        assertThat(existing.getRemovedAt()).isEmpty();
        verify(meetingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void reAddingRemovedAccountCreatesFreshNeedsActionInvitee() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);
        stubActiveInvitees(List.of());

        Result<UpdateMeetingInviteesResult, MeetingError> result =
                service.execute(command(meeting, invitee("bob@test.com", "bob", "Bob")));

        assertThat(result.isSuccess()).isTrue();
        UpdateMeetingInviteesResult.Invitee snapshot =
                success(result).invitees().getFirst();
        assertThat(snapshot.status()).isEqualTo("NEEDS_ACTION");
        assertThat(snapshot.respondedAt()).isNull();
        assertThat(capturedEvents(meeting).getFirst())
                .isInstanceOf(MeetingInvitationsCreatedEvent.class);
    }

    @Test
    void mixedChangePublishesEachNonEmptyGroupOnceWithOnlyItsInvitees() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);
        MeetingInvitee toUpdate = existing("bob@test.com", "bob", "Bob");
        MeetingInvitee toRemove = existing("carol@test.com", "carol", "Carol");
        stubActiveInvitees(List.of(toUpdate, toRemove));

        Result<UpdateMeetingInviteesResult, MeetingError> result = service.execute(command(
                meeting,
                invitee("bob@test.com", "bob", "Bobby"),
                invitee("dave@test.com", "dave", "Dave")));

        assertThat(result.isSuccess()).isTrue();
        List<DomainEvent> events = capturedEvents(meeting);
        assertThat(events).hasSize(3);

        MeetingInvitationsCreatedEvent createdEvent =
                event(events, MeetingInvitationsCreatedEvent.class);
        assertThat(createdEvent.invitees()).hasSize(1);
        assertThat(createdEvent.invitees().getFirst().accountId()).isEqualTo("dave");

        MeetingInvitationsUpdatedEvent updatedEvent =
                event(events, MeetingInvitationsUpdatedEvent.class);
        assertThat(updatedEvent.invitees()).hasSize(1);
        assertThat(updatedEvent.invitees().getFirst().accountId()).isEqualTo("bob");
        assertThat(updatedEvent.invitees().getFirst().displayName()).isEqualTo("Bobby");

        MeetingInvitationsDeletedEvent deletedEvent =
                event(events, MeetingInvitationsDeletedEvent.class);
        assertThat(deletedEvent.invitees()).hasSize(1);
        assertThat(deletedEvent.invitees().getFirst().accountId()).isEqualTo("carol");
    }

    @Test
    void emptyListRemovesAllInviteesAndReturnsEmptyList() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);
        MeetingInvitee first = existing("bob@test.com", "bob", "Bob");
        MeetingInvitee second = existing("carol@test.com", "carol", "Carol");
        stubActiveInvitees(List.of(first, second));

        Result<UpdateMeetingInviteesResult, MeetingError> result =
                service.execute(command(meeting));

        assertThat(result.isSuccess()).isTrue();
        assertThat(success(result).invitees()).isEmpty();
        assertThat(first.getRemovedAt()).isPresent();
        assertThat(second.getRemovedAt()).isPresent();
        MeetingInvitationsDeletedEvent deletedEvent =
                event(capturedEvents(meeting), MeetingInvitationsDeletedEvent.class);
        assertThat(deletedEvent.invitees()).hasSize(2);
    }

    @Test
    void nonHostIsRejectedWithoutChangeOrEvent() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);

        Result<UpdateMeetingInviteesResult, MeetingError> result =
                service.execute(new UpdateMeetingInviteesCommand(
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
    void nonScheduledStatusIsRejectedWithoutChangeOrEvent() {
        Meeting meeting = scheduledMeeting();
        meeting.start();
        meeting.clearDomainEvents();
        stubMeeting(meeting);

        Result<UpdateMeetingInviteesResult, MeetingError> result =
                service.execute(command(meeting, invitee("bob@test.com", "bob", "Bob")));

        assertThat(failure(result)).isInstanceOf(MeetingError.InvalidStatusTransition.class);
        verify(inviteeRepository, never()).saveAll(anyList());
        verify(meetingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void duplicateAccountIdIsRejectedWithoutChangeOrEvent() {
        Meeting meeting = scheduledMeeting();
        stubMeeting(meeting);

        Result<UpdateMeetingInviteesResult, MeetingError> result = service.execute(command(
                meeting,
                invitee("bob@test.com", "bob", "Bob"),
                invitee("bob2@test.com", "bob", "Bob Two")));

        assertThat(failure(result)).isInstanceOf(MeetingError.InvalidSettings.class);
        verify(inviteeRepository, never()).saveAll(anyList());
        verify(meetingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void unknownMeetingIsRejected() {
        when(meetingRepository.findByIdWithLock(any())).thenReturn(Optional.empty());

        Result<UpdateMeetingInviteesResult, MeetingError> result = service.execute(
                new UpdateMeetingInviteesCommand(UUID.randomUUID(), HOST, TENANT, List.of()));

        assertThat(failure(result)).isInstanceOf(MeetingError.MeetingNotFound.class);
        verifyNoInteractions(eventPublisher);
    }

    private void stubMeeting(Meeting meeting) {
        when(meetingRepository.findByIdWithLock(meeting.getId().value()))
                .thenReturn(Optional.of(meeting));
    }

    private void stubActiveInvitees(List<MeetingInvitee> invitees) {
        when(inviteeRepository.findByMeetingId(any())).thenReturn(new ArrayList<>(invitees));
    }

    private UpdateMeetingInviteesCommand command(
            Meeting meeting, UpdateMeetingInviteesCommand.Invitee... invitees) {
        return new UpdateMeetingInviteesCommand(
                meeting.getId().value(), HOST, TENANT, List.of(invitees));
    }

    private UpdateMeetingInviteesCommand.Invitee invitee(
            String email, String accountId, String displayName) {
        return new UpdateMeetingInviteesCommand.Invitee(email, accountId, displayName);
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

    private UpdateMeetingInviteesResult success(
            Result<UpdateMeetingInviteesResult, MeetingError> result) {
        return ((Result.Success<UpdateMeetingInviteesResult, MeetingError>) result).value();
    }

    private MeetingError failure(Result<UpdateMeetingInviteesResult, MeetingError> result) {
        return ((Result.Failure<UpdateMeetingInviteesResult, MeetingError>) result).error();
    }
}
