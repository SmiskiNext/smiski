package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import io.github.smiskinext.meet.application.command.ScheduleMeetingCommand;
import io.github.smiskinext.meet.application.helper.ShortCodeAllocator;
import io.github.smiskinext.meet.application.result.ScheduleMeetingResult;
import io.github.smiskinext.meet.application.service.ScheduleMeetingApplicationService;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.event.MeetingCreatedEvent;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meet.domain.port.InviteTokenGenerator;
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.AggregateRoot;
import io.github.smiskinext.shared.domain.DomainEvent;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ScheduleMeetingApplicationServiceTest {

    private MeetingRepository meetingRepository;
    private MeetingInviteeRepository meetingInviteeRepository;
    private EventPublisher eventPublisher;
    private InviteTokenGenerator inviteTokenGenerator;
    private ShortCodeAllocator shortCodeAllocator;
    private ScheduleMeetingApplicationService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        meetingInviteeRepository = mock(MeetingInviteeRepository.class);
        eventPublisher = mock(EventPublisher.class);
        inviteTokenGenerator = mock(InviteTokenGenerator.class);
        shortCodeAllocator = mock(ShortCodeAllocator.class);

        when(meetingRepository.save(any(Meeting.class))).thenAnswer(inv -> inv.getArgument(0));
        when(meetingInviteeRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(inviteTokenGenerator.generate())
                .thenReturn(new InviteTokenGenerator.TokenResult(
                        "raw-token", "hash-abc", Instant.now().plusSeconds(86400)));
        when(shortCodeAllocator.allocate(any())).thenAnswer(inv -> {
            Function<ShortCode, Object> action = inv.getArgument(0);
            return Optional.of(action.apply(ShortCode.of("abc123def0")));
        });

        service = new ScheduleMeetingApplicationService(
                meetingRepository,
                meetingInviteeRepository,
                eventPublisher,
                inviteTokenGenerator,
                shortCodeAllocator);
    }

    @Test
    void successfulScheduledCreationYieldsScheduledMeetingNoLiveKitAndEventsPublished() {
        Instant now = Instant.now();
        ScheduleMeetingCommand command = validCommand(now);

        Result<ScheduleMeetingResult, MeetingError> result = service.execute(command);

        assertThat(result.isSuccess()).isTrue();
        ScheduleMeetingResult value =
                ((Result.Success<ScheduleMeetingResult, MeetingError>) result).value();
        assertThat(value.type()).isEqualTo("SCHEDULED");
        assertThat(value.status()).isEqualTo("SCHEDULED");
        assertThat(value.hostId()).isEqualTo("host-account");
        assertThat(value.startTime()).isEqualTo(now.plus(1, ChronoUnit.HOURS));
        assertThat(value.endTime()).isEqualTo(now.plus(2, ChronoUnit.HOURS));
        assertThat(value.title()).isEqualTo("Sprint Planning");

        verify(meetingRepository).save(any(Meeting.class));
        verify(eventPublisher).publishEventsOf(any(AggregateRoot.class));

        ArgumentCaptor<Meeting> meetingCaptor = ArgumentCaptor.forClass(Meeting.class);
        verify(meetingRepository).save(meetingCaptor.capture());
        Meeting savedMeeting = meetingCaptor.getValue();

        List<DomainEvent> events = savedMeeting.getDomainEvents();
        assertThat(events).isNotEmpty();
        assertThat(events.getFirst()).isInstanceOf(MeetingCreatedEvent.class);
        MeetingCreatedEvent createdEvent = (MeetingCreatedEvent) events.getFirst();
        assertThat(createdEvent.startTime()).isEqualTo(now.plus(1, ChronoUnit.HOURS));
        assertThat(createdEvent.endTime()).isEqualTo(now.plus(2, ChronoUnit.HOURS));
    }

    @Test
    void inviteesPersistedWithHashedTokensAndInvitationsEventEnqueued() {
        Instant now = Instant.now();
        ScheduleMeetingCommand command = new ScheduleMeetingCommand(
                "tenant-1",
                "Sprint Planning",
                "Daily standup for team",
                new ScheduleMeetingCommand.IssueLink("ISS-1", "PROJ-1", "PROJ"),
                new ScheduleMeetingCommand.Settings("ALLOW_ALL", 50, true, true, true, true),
                "host-account",
                "host@example.com",
                "Host User",
                new ScheduleMeetingCommand.TimeRange(
                        now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS)),
                "Asia/Ho_Chi_Minh",
                List.of(
                        new ScheduleMeetingCommand.Invitee("bob@test.com", "bob-account", "Bob"),
                        new ScheduleMeetingCommand.Invitee(
                                "carol@test.com", "carol-account", "Carol")));

        Result<ScheduleMeetingResult, MeetingError> result = service.execute(command);

        assertThat(result.isSuccess()).isTrue();
        verify(meetingInviteeRepository).saveAll(argThat(list -> list.size() == 2));
        verify(inviteTokenGenerator, times(2)).generate();
    }

    @Test
    void emptyInviteesProducesNoInvitationsEvent() {
        Instant now = Instant.now();
        ScheduleMeetingCommand command = validCommand(now);

        Result<ScheduleMeetingResult, MeetingError> result = service.execute(command);

        assertThat(result.isSuccess()).isTrue();
        verify(meetingInviteeRepository, never()).saveAll(anyList());
    }

    @Test
    void shortCodeExhaustionFailsWithoutPersisting() {
        doReturn(Optional.empty()).when(shortCodeAllocator).allocate(any());

        Instant now = Instant.now();
        ScheduleMeetingCommand command = validCommand(now);

        Result<ScheduleMeetingResult, MeetingError> result = service.execute(command);

        assertThat(result.isFailure()).isTrue();
        MeetingError error = ((Result.Failure<ScheduleMeetingResult, MeetingError>) result).error();
        assertThat(error).isInstanceOf(MeetingError.ShortCodeExhausted.class);
        verify(meetingRepository, never()).save(any());
    }

    private ScheduleMeetingCommand validCommand(Instant now) {
        return new ScheduleMeetingCommand(
                "tenant-1",
                "Sprint Planning",
                "Daily standup for team",
                new ScheduleMeetingCommand.IssueLink("ISS-1", "PROJ-1", "PROJ"),
                new ScheduleMeetingCommand.Settings("ALLOW_ALL", 50, true, true, true, true),
                "host-account",
                "host@example.com",
                "Host User",
                new ScheduleMeetingCommand.TimeRange(
                        now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS)),
                "Asia/Ho_Chi_Minh",
                List.of());
    }
}
